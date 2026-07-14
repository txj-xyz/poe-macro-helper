package overlay.window

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.opengl.GL
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.MemoryUtil.NULL
import javax.imageio.ImageIO

/**
 * Owns the GLFW window + GL context. Transparent framebuffer + undecorated so
 * only ImGui-drawn pixels are opaque. GLFW_FLOATING is left on for now
 * (dev-only, global always-on-top) - phase 3 replaces it with per-frame
 * SetWindowPos against the picked target window's HWND, since the real
 * requirement is "on top of the target window only", not global topmost.
 */
class OverlayWindow(title: String) {
    val handle: Long

    init {
        check(glfwInit()) { "Unable to initialize GLFW" }

        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE)
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE)
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        glfwWindowHint(GLFW_FOCUS_ON_SHOW, GLFW_FALSE)
        glfwWindowHint(GLFW_SAMPLES, 0)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)

        val primaryMonitor = glfwGetPrimaryMonitor()
        check(primaryMonitor != NULL) { "Unable to find the primary monitor" }
        val videoMode = checkNotNull(glfwGetVideoMode(primaryMonitor)) { "Unable to read the primary monitor mode" }

        handle = glfwCreateWindow(videoMode.width(), videoMode.height(), title, NULL, NULL)
        check(handle != NULL) { "Failed to create the GLFW window" }
        applyWindowIcon()

        MemoryStack.stackPush().use { stack ->
            val x = stack.mallocInt(1)
            val y = stack.mallocInt(1)
            glfwGetMonitorPos(primaryMonitor, x, y)
            glfwSetWindowPos(handle, x[0], y[0])
        }

        glfwMakeContextCurrent(handle)
        glfwSwapInterval(1)
        glfwShowWindow(handle)
        GL.createCapabilities()
    }

    fun shouldClose(): Boolean = glfwWindowShouldClose(handle)

    fun requestClose() = glfwSetWindowShouldClose(handle, true)

    fun pollEvents() = glfwPollEvents()

    fun swapBuffers() = glfwSwapBuffers(handle)

    /**
     * jpackage owns the release launcher's icon. This sets the native GLFW
     * window icon as well, which is the icon Windows sees when the app is run
     * directly from Gradle or an IDE during development.
     */
    private fun applyWindowIcon() {
        try {
            val resource = requireNotNull(
                OverlayWindow::class.java.getResource("/icons/macro-overlay.png"),
            ) { "Missing window icon resource: /icons/macro-overlay.png" }
            val image = requireNotNull(ImageIO.read(resource)) {
                "Unable to decode window icon resource"
            }
            val pixels = MemoryUtil.memAlloc(image.width * image.height * 4)
            try {
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        val argb = image.getRGB(x, y)
                        pixels.put(((argb shr 16) and 0xff).toByte())
                        pixels.put(((argb shr 8) and 0xff).toByte())
                        pixels.put((argb and 0xff).toByte())
                        pixels.put(((argb ushr 24) and 0xff).toByte())
                    }
                }
                pixels.flip()

                MemoryStack.stackPush().use { stack ->
                    val icons = GLFWImage.malloc(1, stack)
                    icons.position(0)
                        .width(image.width)
                        .height(image.height)
                        .pixels(pixels)
                    glfwSetWindowIcon(handle, icons)
                }
            } finally {
                MemoryUtil.memFree(pixels)
            }
        } catch (exception: Exception) {
            System.err.println("GLFW window icon could not be applied: ${exception.message}")
        }
    }

    fun destroy() {
        glfwDestroyWindow(handle)
        glfwTerminate()
    }
}
