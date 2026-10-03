// Draws every icon with Compose Desktop and compares it with Tabler's own SVG
// drawn by Chromium. Run through `run.sh`, which renders the SVGs first.
plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
}

dependencies {
    implementation("io.kontour:tabler-icons-outline")
    implementation("io.kontour:tabler-icons-filled")
    implementation(compose.desktop.currentOs)
}

// The classpath, for `run.sh` to start the check with `java` itself once Gradle
// has exited: two JVMs at once — Gradle's 3 GB and this one's — is more than a
// 7 GB runner wants. Not `installDist`, which refuses the two Compose jars that
// share a file name.
val classpathFile = layout.buildDirectory.file("classpath.txt")
tasks.register("writeClasspath") {
    val runtime = sourceSets.main.get().runtimeClasspath
    inputs.files(runtime)
    outputs.file(classpathFile)
    doLast { classpathFile.get().asFile.writeText(runtime.files.joinToString(File.pathSeparator)) }
}
