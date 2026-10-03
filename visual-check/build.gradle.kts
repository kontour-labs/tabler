// Draws every icon with Compose Desktop and compares it with Tabler's own SVG
// drawn by Chromium. Run through `run.sh`, which renders the SVGs first.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    application
}

dependencies {
    implementation("io.kontour:tabler-icons-outline")
    implementation("io.kontour:tabler-icons-filled")
    implementation(compose.desktop.currentOs)
}

application {
    mainClass.set("VisualCheckKt")
    applicationDefaultJvmArgs = listOf("-Xmx2g", "-Djava.awt.headless=true")
}
