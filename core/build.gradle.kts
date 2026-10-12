import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.mavenPublish)
}

// `Tabler`, `Tabler.Outline` and `Tabler.Filled`, which the icon sets extend.
// No dependencies at all: nothing in it is Compose.
kontourPublishing(
    displayName = "Tabler Icons for Compose: core",
    summary = "The Tabler, Tabler.Outline and Tabler.Filled objects the Tabler icon sets extend.",
)

// Maven Central: see `kontourPublishing` in buildSrc for the rest. Signed
// wherever there is a key, which CI always has and Central insists on; without
// one, as for a `publishToMavenLocal` dry run, unsigned.
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
}

kotlin {
    explicitApi()

    // The six targets kontour-labs/ui builds for. Every one of them, in all three
    // modules: an icon missing on one target is a library that does not compile
    // there.
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
        namespace = "io.kontour.icons.tabler"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
}
