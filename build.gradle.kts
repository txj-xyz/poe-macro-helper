plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

group = "overlay"
version = "1.0.0"

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.6"
val imguiVersion = "1.89.0"
val jnaVersion = "5.17.0"
val lwjglNatives = "natives-windows"

dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)

    implementation("io.github.spair:imgui-java-binding:$imguiVersion")
    implementation("io.github.spair:imgui-java-lwjgl3:$imguiVersion")
    runtimeOnly("io.github.spair:imgui-java-natives-windows:$imguiVersion")

    implementation("net.java.dev.jna:jna:$jnaVersion")
    implementation("net.java.dev.jna:jna-platform:$jnaVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("overlay.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
