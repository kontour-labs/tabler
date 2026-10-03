rootProject.name = "kontour-tabler-icons"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Three published modules. The project name is the artifactId, so each is
// included under the name it is published as and pointed at a short directory.
//
// `Tabler`, `Tabler.Outline` and `Tabler.Filled`. Its own artifact so that the
// outline and filled sets can each be used without the other, and without both
// declaring the same classes.
include(":tabler-icons-core")
project(":tabler-icons-core").projectDir = file("core")

// Every icon, generated at build time from the Tabler release pinned in
// `gradle.properties`. Neither module has any source of its own.
include(":tabler-icons-outline")
project(":tabler-icons-outline").projectDir = file("outline")

include(":tabler-icons-filled")
project(":tabler-icons-filled").projectDir = file("filled")
