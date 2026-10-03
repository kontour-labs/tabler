import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Publishing, for every module: the version, the coordinate task, the repository
 * and the POM. The same rules as `kontour-labs/ui`'s, with one addition: the
 * version is Tabler's.
 *
 * ### The version
 *
 * A release is the Tabler release it was generated from. Three sources, in this
 * order:
 *
 *  1. `-Pkontour.version=…`, for a deliberate publish that is not a tag.
 *  2. The tag being built — and **only** a tag (`GITHUB_REF_TYPE`), so that a
 *     branch build can never take the branch name as its version.
 *  3. `<tabler.version>-SNAPSHOT`, for everything else.
 *
 * A release version must then be `tabler.version` itself (`3.48.0`) or that with
 * a fourth part (`3.48.0.1`), which is how a fix to the generator is re-released
 * against the same icons. Anything else would claim one Tabler release and ship
 * another, so it fails before anything is uploaded — an unpublish on a package
 * registry is a support ticket, and the check costs a string comparison.
 *
 * Everything is read through `providers`, never `System.getenv`: the
 * configuration cache is on, and a direct environment read at configuration
 * time is invisible to it.
 *
 * **This publishes from Linux.** Kotlin cross-compiles Apple *klibs*, which is
 * what a library consumer resolves; only linking a framework needs Xcode, and
 * that happens in the consuming app.
 */
fun Project.kontourPublishing(displayName: String, summary: String) {
    val tabler = tablerVersion()
    group = "io.kontour"
    version = providers.gradleProperty("kontour.version")
        .orElse(
            providers.environmentVariable("GITHUB_REF_TYPE")
                .zip(providers.environmentVariable("GITHUB_REF_NAME")) { type, name ->
                    if (type == "tag") name.removePrefix("v") else ""
                }
                .filter { it.isNotEmpty() }
        )
        .orElse("$tabler-SNAPSHOT")
        .map { candidate ->
            require(candidate == "$tabler-SNAPSHOT" || Regex("""^${Regex.escape(tabler)}(?:\.\d+)?$""").matches(candidate)) {
                "`$candidate` is not a publishable version of icons generated from Tabler $tabler. " +
                    "A release is `v$tabler`, or `v$tabler.1` and up to re-release the same icons; " +
                    "to publish another Tabler release, change `tabler.version` (and " +
                    "`tabler.integrity`) in gradle.properties first."
            }
            candidate
        }
        .get()

    // What this build would publish as:
    //
    //     ./gradlew :tabler-icons-outline:coordinate
    val coordinate = "$group:$name:$version"
    tasks.register("coordinate") {
        group = "help"
        description = "Prints the group:artifact:version this build would publish as."
        // Captured above, at configuration time: a `doLast` that read `project`
        // would hold the project reference and break the configuration cache.
        doLast { println(coordinate) }
    }

    extensions.configure<PublishingExtension> {
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/kontour-labs/tabler-icons")
                credentials {
                    username = providers.environmentVariable("GITHUB_ACTOR").orNull
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull
                }
            }
        }

        publications.withType<MavenPublication>().configureEach {
            pom {
                name.set(displayName)
                description.set(summary)
                url.set("https://github.com/kontour-labs/tabler-icons")
                licenses {
                    license {
                        // The icons are Tabler's, converted; the licence is theirs.
                        name.set("MIT")
                        url.set("https://github.com/tabler/tabler-icons/blob/main/LICENSE")
                        comments.set("Tabler Icons, Copyright (c) 2020-2026 Paweł Kuna.")
                    }
                }
                scm {
                    url.set("https://github.com/kontour-labs/tabler-icons")
                    connection.set("scm:git:https://github.com/kontour-labs/tabler-icons.git")
                }
            }
        }
    }
}

/** The Tabler release the icons are generated from, `tabler.version` in gradle.properties. */
fun Project.tablerVersion(): String = providers.gradleProperty("tabler.version").get().also {
    require(Regex("""^\d+\.\d+\.\d+$""").matches(it)) {
        "`tabler.version` should be a Tabler release, like 3.48.0; it is `$it`."
    }
}
