plugins {
    // Loaded once here so subproject classloaders share them.
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}
