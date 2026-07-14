package overlay.ui

import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean

/**
 * Keeps every managed ImGui window inside the target client area and records
 * the exact visible window rectangles used by the native click-through logic.
 */
object WindowBounds {
    data class WindowRegion(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private data class WindowState(
        var initialized: Boolean = false,
        var dragging: Boolean = false,
        var dragStartMouseX: Float = 0f,
        var dragStartMouseY: Float = 0f,
        var dragStartWindowX: Float = 0f,
        var dragStartWindowY: Float = 0f,
        var lastPosX: Float = 0f,
        var lastPosY: Float = 0f,
        var lastSizeX: Float = 0f,
        var lastSizeY: Float = 0f,
    )

    private data class HitRegion(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom
    }

    private val states = mutableMapOf<String, WindowState>()
    private val hitRegions = mutableListOf<HitRegion>()
    private var mouseInteractionHeld = false

    /** Clear regions before building this frame's visible windows. */
    fun beginFrame() {
        hitRegions.clear()
    }

    /** Forget all runtime positions/sizes so windows use their defaults next frame. */
    fun resetLayout() {
        states.clear()
        mouseInteractionHeld = false
    }

    /**
     * Begin a bounded window. Fixed dimensions are re-applied every frame;
     * default dimensions are only applied on the first frame and remain user-resizable.
     */
    fun begin(
        title: String,
        defaultX: Float,
        defaultY: Float,
        defaultWidth: Float,
        defaultHeight: Float,
        fixedSize: Boolean,
        flags: Int = ImGuiWindowFlags.None,
        open: ImBoolean? = null,
    ): Boolean {
        val state = states.getOrPut(title) { WindowState() }
        val display = ImGui.getIO().displaySize
        val requestedWidth = defaultWidth.coerceAtMost(display.x).coerceAtLeast(1f)
        val requestedHeight = defaultHeight.coerceAtMost(display.y).coerceAtLeast(1f)

        if (!state.initialized) {
            ImGui.setNextWindowPos(
                defaultX.coerceIn(0f, (display.x - requestedWidth).coerceAtLeast(0f)),
                defaultY.coerceIn(0f, (display.y - requestedHeight).coerceAtLeast(0f)),
                ImGuiCond.Always,
            )
        }

        if (fixedSize) {
            ImGui.setNextWindowSize(requestedWidth, requestedHeight, ImGuiCond.Always)
        } else if (!state.initialized) {
            ImGui.setNextWindowSize(requestedWidth, requestedHeight, ImGuiCond.Always)
        }

        updateBoundedDrag(state, display.x, display.y)

        var effectiveFlags = flags or ImGuiWindowFlags.NoMove
        if (fixedSize) effectiveFlags = effectiveFlags or ImGuiWindowFlags.NoResize
        val contentsVisible = if (open != null) {
            ImGui.begin(title, open, effectiveFlags)
        } else {
            ImGui.begin(title, effectiveFlags)
        }

        val size = ImGui.getWindowSize()
        val width = size.x.coerceAtMost(display.x).coerceAtLeast(1f)
        val height = size.y.coerceAtMost(display.y).coerceAtLeast(1f)
        if (width != size.x || height != size.y) ImGui.setWindowSize(width, height)

        val pos = ImGui.getWindowPos()
        val x = pos.x.coerceIn(0f, (display.x - width).coerceAtLeast(0f))
        val y = pos.y.coerceIn(0f, (display.y - height).coerceAtLeast(0f))
        if (x != pos.x || y != pos.y) ImGui.setWindowPos(x, y)

        state.initialized = true
        state.lastPosX = x
        state.lastPosY = y
        state.lastSizeX = width
        state.lastSizeY = height

        // A small border allowance keeps resize grips and window edges interactive.
        hitRegions += HitRegion(x - 4f, y - 4f, x + width + 4f, y + height + 4f)
        return contentsVisible
    }

    fun isCursorOverVisibleWindow(): Boolean {
        val mouse = ImGui.getIO().mousePos
        return hitRegions.any { it.contains(mouse.x, mouse.y) }
    }

    fun isDraggingAnyWindow(): Boolean = states.values.any { it.dragging }

    /**
     * Latches a left-button interaction that began over any panel. This also
     * covers ImGui resize grips, whose cursor can move beyond the old bounds
     * before the expanded window is rendered.
     */
    fun isManipulatingAnyWindow(): Boolean {
        if (!ImGui.isMouseDown(0)) {
            mouseInteractionHeld = false
        } else if (isDraggingAnyWindow() || isCursorOverVisibleWindow()) {
            mouseInteractionHeld = true
        }
        return mouseInteractionHeld || isDraggingAnyWindow()
    }

    /** Native-window regions for the currently rendered ImGui windows. */
    fun visibleWindowRegions(): List<WindowRegion> = hitRegions.map {
        WindowRegion(
            left = kotlin.math.floor(it.left.toDouble()).toInt(),
            top = kotlin.math.floor(it.top.toDouble()).toInt(),
            right = kotlin.math.ceil(it.right.toDouble()).toInt(),
            bottom = kotlin.math.ceil(it.bottom.toDouble()).toInt(),
        )
    }

    private fun updateBoundedDrag(state: WindowState, displayWidth: Float, displayHeight: Float) {
        if (!state.initialized) return

        val mouse = ImGui.getIO().mousePos
        val overTitleBar = mouse.x >= state.lastPosX && mouse.x <= state.lastPosX + state.lastSizeX &&
            mouse.y >= state.lastPosY && mouse.y <= state.lastPosY + ImGui.getFrameHeight()

        if (!state.dragging && !isDraggingAnyWindow() && overTitleBar && ImGui.isMouseClicked(0)) {
            state.dragging = true
            state.dragStartMouseX = mouse.x
            state.dragStartMouseY = mouse.y
            state.dragStartWindowX = state.lastPosX
            state.dragStartWindowY = state.lastPosY
        }

        if (!state.dragging) return
        if (!ImGui.isMouseDown(0)) {
            state.dragging = false
            return
        }

        val maxX = (displayWidth - state.lastSizeX).coerceAtLeast(0f)
        val maxY = (displayHeight - state.lastSizeY).coerceAtLeast(0f)
        val x = (state.dragStartWindowX + mouse.x - state.dragStartMouseX).coerceIn(0f, maxX)
        val y = (state.dragStartWindowY + mouse.y - state.dragStartMouseY).coerceIn(0f, maxY)
        ImGui.setNextWindowPos(x, y, ImGuiCond.Always)
    }
}
