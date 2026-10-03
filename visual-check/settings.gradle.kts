rootProject.name = "tabler-visual-check"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

// The icons, built from this checkout rather than resolved from a registry:
// `io.kontour:tabler-icons-*` below are these projects.
includeBuild("..")
