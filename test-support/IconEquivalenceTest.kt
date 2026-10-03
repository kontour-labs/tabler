package io.kontour.icons.tabler

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorPath
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every generated icon, held to what Compose itself makes of Tabler's data.
 *
 * The generator turns path data into `PathBuilder` calls at build time; Compose
 * can parse the same strings at runtime with `PathParser`. Two independent
 * readings of the same SVG grammar, so where they agree on every path of every
 * icon, the generator's reading is right — and where they disagree, this names
 * the icon and the first node that differs.
 *
 * The fixture is written by the generator next to the sources: one line per
 * icon, `name, property, path…`, each path `<fill><stroke>;<alpha>;<evenOdd>;<d>`.
 * Shared by both sets; `tabler.style` says which this run is.
 */
class IconEquivalenceTest {

    private val style = System.getProperty("tabler.style") ?: error("Run through Gradle: tabler.style is unset.")
    private val receiver: Any = when (style) {
        "outline" -> Tabler.Outline
        "filled" -> Tabler.Filled
        else -> error("Unknown style $style")
    }

    @Test
    fun everyIconDrawsWhatTablerDrew() {
        val fixture = javaClass.getResource("/tabler-$style.tsv") ?: fail("No fixture for $style")
        val lines = fixture.readText().lines().filter { it.isNotEmpty() }
        assertTrue(lines.size > 500, "Only ${lines.size} $style icons in the fixture.")

        val problems = mutableListOf<String>()
        for (line in lines) {
            val fields = line.split('\t')
            val (name, property) = fields
            val problem = runCatching { compare(property, fields.drop(2)) }
                .fold(onSuccess = { it }, onFailure = { "threw $it" })
            if (problem != null) problems += "$name ($property): $problem"
        }

        assertTrue(
            problems.isEmpty(),
            "${problems.size} of ${lines.size} $style icons differ from Tabler:\n" +
                problems.take(25).joinToString("\n"),
        )
    }

    private fun compare(property: String, expected: List<String>): String? {
        val vector = load(property)
        if (vector.viewportWidth != 24f || vector.viewportHeight != 24f) return "viewport is not 24×24"

        // An icon with a stroke starts with the primer, which draws nothing and makes
        // Compose's Skia renderer honour round joins: see `IconFile` in buildSrc.
        var paths = vector.root.toList()
        if (expected.any { it.split(';')[0][1] == '1' }) {
            val primer = paths.firstOrNull() as? VectorPath ?: return "no primer path"
            val inert = primer.fill == null && primer.stroke == SolidColor(Color.Transparent) &&
                primer.strokeLineJoin == StrokeJoin.Miter && primer.strokeLineWidth > 0f &&
                primer.pathData == listOf(PathNode.MoveTo(0f, 0f))
            if (!inert) return "the first path is not the primer: $primer"
            paths = paths.drop(1)
        }
        if (paths.size != expected.size) return "${paths.size} paths, expected ${expected.size}"

        paths.zip(expected).forEachIndexed { index, (node, spec) ->
            val path = node as? VectorPath ?: return "node $index is a ${node::class.simpleName}"
            val (paint, alphaText, evenOdd, d) = spec.split(';', limit = 4)
            val fills = paint[0] == '1'
            val strokes = paint[1] == '1'
            val alpha = alphaText.toFloatOrNull() ?: 1f

            val problem = when {
                fills != (path.fill == black) -> "path $index fill is ${path.fill}"
                strokes != (path.stroke == black) -> "path $index stroke is ${path.stroke}"
                fills && path.fillAlpha != alpha -> "path $index fillAlpha is ${path.fillAlpha}"
                strokes && path.strokeAlpha != alpha -> "path $index strokeAlpha is ${path.strokeAlpha}"
                strokes && path.strokeLineWidth != 2f -> "path $index stroke width is ${path.strokeLineWidth}"
                strokes && path.strokeLineCap != StrokeCap.Round -> "path $index cap is ${path.strokeLineCap}"
                strokes && path.strokeLineJoin != StrokeJoin.Round -> "path $index join is ${path.strokeLineJoin}"
                (evenOdd == "1") != (path.pathFillType == PathFillType.EvenOdd) -> "path $index fill type is ${path.pathFillType}"
                else -> nodesDiffer(path.pathData, PathParser().parsePathString(d).toNodes())?.let { "path $index: $it" }
            }
            if (problem != null) return problem
        }
        return null
    }

    /** `Tabler.Outline.Abc` is `AbcKt.getAbc(Tabler.Outline)` to the JVM. */
    private fun load(property: String): ImageVector {
        val owner = Class.forName("io.kontour.icons.tabler.$style.${property}Kt")
        return owner.getMethod("get$property", receiver.javaClass).invoke(null, receiver) as ImageVector
    }

    /**
     * Each node by its class and fields, read from the data class's `toString`
     * (`RelativeArcTo(horizontalEllipseRadius=2.0, …, isMoreThanHalf=true, …)`),
     * with numbers compared to a tolerance: the generator's literals are
     * rounded by the Kotlin compiler and Compose's by its own float parser.
     */
    private fun nodesDiffer(actual: List<PathNode>, expected: List<PathNode>): String? {
        if (actual.size != expected.size) return "${actual.size} nodes, expected ${expected.size}"
        actual.zip(expected).forEachIndexed { index, (a, e) ->
            val same = a::class == e::class && fields(a).zip(fields(e)).all { (x, y) ->
                val xf = x.toFloatOrNull()
                val yf = y.toFloatOrNull()
                if (xf != null && yf != null) abs(xf - yf) <= 1e-4f else x == y
            }
            if (!same) return "node $index is $a, expected $e"
        }
        return null
    }

    private fun fields(node: PathNode): List<String> =
        node.toString().substringAfter('(', "").substringBeforeLast(')', "")
            .split(", ").filter { it.isNotEmpty() }.map { it.substringAfter('=') }

    private val black = SolidColor(Color(0xFF000000))
}
