import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import tabler.GenerateTablerIcons

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    `maven-publish`
}

kontourPublishing(
    displayName = "Tabler Icons for Compose: filled",
    summary = "Every filled Tabler icon as a Compose Multiplatform ImageVector, Tabler.Filled.<Name>.",
)


// Every icon, generated from the Tabler release in gradle.properties. There is
// no source in this module: see `buildSrc/src/main/kotlin/tabler/`.
val generate = tasks.register<GenerateTablerIcons>("generateTablerIcons") {
    description = "Generates an ImageVector for every filled Tabler icon."
    tablerVersion = providers.gradleProperty("tabler.version")
    tablerIntegrity = providers.gradleProperty("tabler.integrity")
    style = "filled"
    packageName = "io.kontour.icons.tabler"
    sourceDir = layout.buildDirectory.dir("generated/tabler/kotlin")
    fixtureDir = layout.buildDirectory.dir("generated/tabler/fixture")
}

kotlin {
    explicitApi()

    // The same six targets as `:tabler-icons-core`.
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "io.kontour.icons.tabler.filled"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain {
            // A provider of the task's output, so compiling (and the sources
            // jar) runs the generator first without being told to.
            kotlin.srcDir(generate.flatMap { it.sourceDir })
            dependencies {
                // `api`: `Tabler.Filled` and `ImageVector` are in every icon's signature.
                api(project(":tabler-icons-core"))
                api(libs.compose.ui)
            }
        }

        // Every icon held to Compose's own reading of Tabler's path data. Shared
        // with the other set, so it lives outside both: `test-support/`.
        jvmTest {
            kotlin.srcDir(rootProject.layout.projectDirectory.dir("test-support"))
            resources.srcDir(generate.flatMap { it.fixtureDir })
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("tabler.style", "filled")
}
