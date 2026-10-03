package tabler

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import javax.inject.Inject

/**
 * Every icon in one Tabler style, as Kotlin source.
 *
 * Reads the npm package `@tabler/icons` at [tablerVersion] — not the GitHub
 * repository, because the package carries `tabler-nodes-<style>.json`, which is
 * each icon already reduced to paths by Tabler's own build. Then one file per
 * icon into [sourceDir] (see [IconFile]), and a fixture into [fixtureDir] for the
 * test that holds those files to Tabler's data (see `test-support/`).
 *
 * Cacheable, and deterministic on purpose: its inputs are the version, the
 * style and the package, so a release that leaves a style alone produces the same
 * files, and every compile downstream of them comes out of the build cache.
 *
 * The tarball is checked against [tablerIntegrity], npm's `dist.integrity`,
 * which the release workflow pins in `gradle.properties` with the version.
 */
@CacheableTask
abstract class GenerateTablerIcons : DefaultTask() {

    /** `3.48.0`. */
    @get:Input
    abstract val tablerVersion: Property<String>

    /** `sha512-…`, as npm reports it. */
    @get:Input
    abstract val tablerIntegrity: Property<String>

    /** `outline` or `filled`. */
    @get:Input
    abstract val style: Property<String>

    /** The package `Tabler` is in. Icons go in `<package>.<style>`. */
    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val fixtureDir: DirectoryProperty

    @get:Inject
    protected abstract val archives: ArchiveOperations

    @get:Inject
    protected abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun generate() {
        val style = Style.of(style.get())
        val version = tablerVersion.get()

        val tarball = temporaryDir.resolve("icons-$version.tgz")
        tarball.writeBytes(download(version))
        val json = archives.tarTree(archives.gzip(tarball))
            .matching { include("package/tabler-nodes-${style.id}.json") }
            .singleFile

        @Suppress("UNCHECKED_CAST")
        val nodes = JsonSlurper().parse(json, "UTF-8") as Map<String, List<*>>
        val icons = nodes.keys.sorted().map { name -> Icons.fromNodes(name, nodes.getValue(name), style) }

        fileSystem.delete { delete(sourceDir, fixtureDir) }
        val sources = sourceDir.get().asFile
        icons.forEach { IconFile.build(it, style, packageName.get()).writeTo(sources) }

        // One line per icon: name, property, then a field per path, which is
        // `<fill><stroke>;<alpha>;<evenOdd>;<d>`. Tabs and semicolons appear in
        // none of Tabler's names or path data.
        val fixture = fixtureDir.get().asFile.resolve("tabler-${style.id}.tsv")
        fixture.parentFile.mkdirs()
        fixture.writeText(
            icons.joinToString("") { icon ->
                val paths = icon.paths.joinToString("\t") { path ->
                    "${path.fill.bit}${path.stroke.bit};${path.alpha.orEmpty()};${path.evenOdd.bit};${path.d}"
                }
                "${icon.name}\t${icon.property}\t$paths\n"
            }
        )

        logger.lifecycle("Generated ${icons.size} ${style.id} icons from Tabler Icons $version.")
    }

    private fun download(version: String): ByteArray {
        val url = "https://registry.npmjs.org/@tabler/icons/-/icons-$version.tgz"
        val client = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build()
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(2)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) {
            "Downloading $url answered HTTP ${response.statusCode()}. Is `tabler.version` a published release?"
        }
        val bytes = response.body()

        val expected = tablerIntegrity.get()
        check(expected.startsWith("sha512-")) {
            "`tabler.integrity` should be npm's `dist.integrity`, `sha512-…`; it is `$expected`."
        }
        val actual = "sha512-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes))
        check(actual == expected) {
            "$url does not match `tabler.integrity`: expected $expected, downloaded $actual. " +
                "If the version was bumped by hand, bump the integrity with it " +
                "(`curl -s https://registry.npmjs.org/@tabler/icons/$version | jq -r .dist.integrity`)."
        }
        return bytes
    }

    private val Boolean.bit: Char get() = if (this) '1' else '0'
}
