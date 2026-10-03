# tabler-icons

Every [Tabler icon](https://tabler.io/icons), outline and filled, as a Compose
Multiplatform `ImageVector`. They're generated from each Tabler release and
published within a few days of it.

```kotlin
import io.kontour.icons.tabler.Tabler
import io.kontour.icons.tabler.outline.Abc
import io.kontour.icons.tabler.filled.Star

Icon(Tabler.Outline.Abc, contentDescription = null)
Icon(Tabler.Filled.Star, contentDescription = null)
```

The API is [composables' `compose-icons`](https://github.com/composablehorizons/compose-icons)
API under a different package, so switching over only changes imports.

Targets: Android, JVM (desktop), iOS (`iosArm64`, `iosSimulatorArm64`), JS and
Wasm, the same six as [Kontour UI](https://github.com/kontour-labs/ui).

## Installing

The packages are on GitHub Packages, which needs credentials even to read: a
token with `read:packages`.

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://maven.pkg.github.com/kontour-labs/tabler-icons") {
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

```toml
# gradle/libs.versions.toml
[versions]
tabler-icons = "3.48.0"

[libraries]
tabler-icons-outline = { module = "io.kontour:tabler-icons-outline", version.ref = "tabler-icons" }
tabler-icons-filled = { module = "io.kontour:tabler-icons-filled", version.ref = "tabler-icons" }
```

| Artifact | What's in it |
| --- | --- |
| `io.kontour:tabler-icons-outline` | `Tabler.Outline.*`: every icon, about 5,200 of them |
| `io.kontour:tabler-icons-filled` | `Tabler.Filled.*`: the 1,050-odd icons that have a filled version |
| `io.kontour:tabler-icons-core` | Just the `Tabler`, `Tabler.Outline` and `Tabler.Filled` objects. Both sets bring it in |

**The version is Tabler's.** `3.48.0` holds the icons of Tabler 3.48.0. A
fourth part (`3.48.0.1`) is a re-release of the same icons with a fix to how
they're generated.

Each set depends on `org.jetbrains.compose.ui:ui` and nothing else. There's no
Compose compiler plugin to match, because none of the icons is a composable.

### Names

An icon's name is Tabler's name in PascalCase: `arrow-big-up-line` is
`Tabler.Outline.ArrowBigUpLine`, and `a-b-2` is `Tabler.Outline.AB2`. Filled
icons have the same names in their own package, so use an import alias where a
file needs both:

```kotlin
import io.kontour.icons.tabler.filled.Star as FilledStar
import io.kontour.icons.tabler.outline.Star
```

Each vector is built the first time it's read and kept, so an app only pays for
the icons it draws.

## Switching from composables' compose-icons

1. Replace `com.composables:icons-tabler-outline-cmp` and
   `com.composables:icons-tabler-filled-cmp` with the two artifacts above.
2. Replace `com.composables.icons.tabler` with `io.kontour.icons.tabler` in
   every import.

The names, the receivers and the packages under them (`.outline`, `.filled`)
are the same. Tabler has renamed and removed some icons since the release
composables last generated from, and those imports fail to compile until
they're updated. [tabler.io/icons](https://tabler.io/icons) has the current names.

## How releases happen

[`release.yml`](.github/workflows/release.yml) runs on Monday mornings (UTC)
and asks npm for Tabler's latest release. If that release is already tagged here,
the job ends after about ten seconds. If it isn't, the workflow does this:

1. Pins it in [`gradle.properties`](gradle.properties): `tabler.version`, and
   `tabler.integrity`, npm's checksum of the tarball.
2. Commits that to `main` as `github-actions[bot]` and tags it `v<version>`.
3. Builds all six targets and publishes to GitHub Packages. It runs no tests.
4. Creates a GitHub release that lists the icons added, redrawn and removed.
   Removals are breaking, and they're flagged as such.

Tests run in [`ci.yml`](.github/workflows/ci.yml) when the code that generates
the icons changes, not on every Tabler release. The generator fails the build
on anything in Tabler's data it doesn't understand, so a format change stops a
release instead of shipping broken icons.

Both workflows are written for a private repository's minute allowance. A week
with nothing new costs one billed minute. A release costs one build, and a set
Tabler left alone comes out of the Gradle build cache instead of being compiled
again.

**By hand:** Actions → Release → Run workflow. Leave the version blank for the
latest, or pick one. Tick **dry run** to build without committing, tagging or
uploading anything.

**A generator fix**, re-released against the same icons: merge it, then
push a tag with a fourth part, `git tag v3.48.0.1 && git push origin v3.48.0.1`.
The build refuses a tag that doesn't match `tabler.version`.

**If main is protected,** the bot can't push its pin. Either let GitHub
Actions bypass the rule, or give the checkout in `release.yml` a deploy key
that's allowed to push.

## Working on it

```sh
./gradlew :buildSrc:test                                        # the generator
./gradlew :tabler-icons-outline:generateTablerIcons             # outline/build/generated/tabler/kotlin
./gradlew :tabler-icons-outline:jvmTest :tabler-icons-filled:jvmTest   # every icon against Compose's parser
./gradlew publishToMavenLocal                                   # all six targets; needs an Android SDK
```

The generator is in [`buildSrc/src/main/kotlin/tabler/`](buildSrc/src/main/kotlin/tabler):

- `GenerateTablerIcons` downloads and verifies the npm tarball.
- `Icon` reads Tabler's normalised `tabler-nodes-<set>.json`.
- `PathData` parses SVG path data into `PathBuilder` calls.
- `IconFile` writes each file with KotlinPoet.

Nothing generated is committed. The icons are built from the pinned release
every time, into `build/`.

## Licence

The icons are Tabler's, under the MIT licence: see [LICENSE](LICENSE).
