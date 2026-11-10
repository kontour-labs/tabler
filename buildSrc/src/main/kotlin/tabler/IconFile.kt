package tabler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec

/**
 * The Kotlin file for one icon, shaped like composables' so that a migration is
 * a change of package and nothing else:
 *
 * ```
 * public val Tabler.Outline.Abc: ImageVector
 *     get() = _Abc ?: tablerIcon("abc", "M3 16v-6a2 2 0 1 1 4 0v6", "M3 13h4", …).also { _Abc = it }
 *
 * private var _Abc: ImageVector? = null
 * ```
 *
 * Built on first use and kept, so an app pays for the icons it draws and not for
 * the six thousand it does not.
 *
 * ### Path data, not code
 *
 * Each path is Tabler's own SVG path data, a string, which the set's
 * [helper][helper] turns into a vector at first use with Compose's
 * `PathParser`. Up to 3.51.0 each path was generated as the `PathBuilder`
 * calls that draw it, in a `path { moveTo(…) … }` lambda of its own: thousands
 * of functions, which on iOS Kotlin/Native compiles to native code one by
 * one. Measured in an iOS release framework, an icon an app draws cost
 * 12.4 KB as code and 3.4 KB as data, and the outline set's klib was 5.5 MB.
 * Changed for 3.51.0.1, the same icons: `IconEquivalenceTest` has always held
 * every generated icon to what `PathParser` makes of Tabler's data, so drawing
 * them through it draws the same thing.
 *
 * A path the set draws its own way — stroked in outline, filled in filled, at
 * full opacity, nonzero — is its data alone. One that differs says how first,
 * `@<f><s><e>a<alpha>;` ahead of the data (`@fe;M0 0h1` is filled, not
 * stroked, even-odd), which no path data starts with.
 *
 * The data is still read here, by [PathData], and anything it does not
 * understand fails the generation by name: the release workflow runs no tests,
 * so what this accepts is what ships.
 *
 * Every space in the code below is `·`, KotlinPoet's non-breaking space. Without
 * it a long line is wrapped wherever KotlinPoet likes, which compiles but reads
 * badly.
 *
 * Nothing here may depend on the Tabler version — not even a comment — because a
 * set Tabler left alone must come out byte for byte the same, which is what lets
 * the build cache skip compiling it.
 */
internal object IconFile {

    private val imageVector = ClassName("androidx.compose.ui.graphics.vector", "ImageVector")

    /** The set's own function that draws an icon from its data: see [helper]. */
    private const val HELPER = "tablerIcon"

    fun build(icon: Icon, style: Style, basePackage: String): FileSpec {
        val receiver = ClassName(basePackage, "Tabler", style.objectName)
        val backing = PropertySpec.builder("_${icon.property}", imageVector.copy(nullable = true), KModifier.PRIVATE)
            .mutable()
            .initializer("null")
            .build()

        val args = CodeBlock.builder().apply {
            add("%S", icon.name)
            icon.paths.forEach { path ->
                PathData.parse(path.d)
                add(",·%S", encode(path, style))
            }
        }.build()
        val getter = FunSpec.getterBuilder()
            .addCode("return·%N·?:·%L(%L).also·{·%N·=·it·}\n", backing, HELPER, args, backing)
            .build()
        val property = PropertySpec.builder(icon.property, imageVector)
            .receiver(receiver)
            .getter(getter)
            .build()

        return FileSpec.builder("$basePackage.${style.id}", icon.property)
            .indent("    ")
            .addProperty(property)
            .addProperty(backing)
            .build()
    }

    /** A path as its set's helper reads it: its data, with how it differs from the set's way first. */
    fun encode(path: IconPath, style: Style): String {
        val alpha = path.alpha?.removeSuffix("f")
        val usual = path.fill == style.fills && path.stroke == style.strokes && alpha == null && !path.evenOdd
        if (usual) return path.d
        val flags = buildString {
            if (path.fill) append('f')
            if (path.stroke) append('s')
            if (path.evenOdd) append('e')
            if (alpha != null) append('a').append(alpha)
        }
        return "@$flags;${path.d}"
    }

    /**
     * The set's `tablerIcon`, which every icon's getter calls: the vector from
     * its name and its paths' data, drawn as the set draws a path unless a path
     * says otherwise (see [encode]). Generated with the set, so it is `internal`
     * to it and nothing is added to the API.
     *
     * ### The primer
     *
     * An icon with any stroke starts with one path that draws nothing: empty, and
     * stroked in transparent with a miter join. It is there because Compose
     * Multiplatform's Skia renderer (desktop, iOS, web) loses `StrokeJoin.Round`
     * otherwise. Its paint records Round as its starting join without passing it
     * to Skia, whose own default is a miter, and a draw only passes a join on that
     * differs from the recorded one, so the first Round never arrives. The corners
     * then come out as spikes: 297 of Tabler's outline icons looked visibly wrong,
     * among them `bell` and `star`, measured against Tabler's SVGs in Chromium
     * (`visual-check/`). Drawing a miter first makes every Round after it a
     * change, which reaches Skia. Android's renderer has no such bug, and on
     * Android, or once Compose is fixed, the primer still costs nothing.
     */
    fun helper(style: Style, basePackage: String): String = """
        |package $basePackage.${style.id}
        |
        |import androidx.compose.ui.graphics.Color
        |import androidx.compose.ui.graphics.PathFillType
        |import androidx.compose.ui.graphics.SolidColor
        |import androidx.compose.ui.graphics.StrokeCap
        |import androidx.compose.ui.graphics.StrokeJoin
        |import androidx.compose.ui.graphics.vector.ImageVector
        |import androidx.compose.ui.graphics.vector.PathNode
        |import androidx.compose.ui.graphics.vector.addPathNodes
        |import androidx.compose.ui.unit.dp
        |
        |/**
        | * An icon of this set from its name and its paths' SVG data, as Tabler drew
        | * it: 24 by 24, each path ${if (style.strokes) "stroked 2 wide with round caps and joins" else "filled"}
        | * in black unless it says otherwise ahead of its data, `@<f><s><e>a<alpha>;`.
        | * Generated with the set; see the generator's `IconFile` for why it is
        | * data and not code, and for the primer.
        | */
        |internal fun $HELPER(name: String, vararg paths: String): ImageVector {
        |    val builder = ImageVector.Builder(
        |        name = name,
        |        defaultWidth = 24.dp,
        |        defaultHeight = 24.dp,
        |        viewportWidth = 24f,
        |        viewportHeight = 24f,
        |    )
        |    val drawn = paths.map(::TablerPath)
        |    // Draws nothing. It makes Compose's Skia renderer honour the round joins below.
        |    if (drawn.any { it.stroke }) {
        |        builder.addPath(
        |            pathData = Primer,
        |            stroke = SolidColor(Color.Transparent),
        |            strokeLineWidth = 1f,
        |            strokeLineJoin = StrokeJoin.Miter,
        |        )
        |    }
        |    for (path in drawn) {
        |        builder.addPath(
        |            pathData = addPathNodes(path.data),
        |            pathFillType = if (path.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        |            fill = if (path.fill) Black else null,
        |            fillAlpha = if (path.fill) path.alpha else 1f,
        |            stroke = if (path.stroke) Black else null,
        |            strokeAlpha = if (path.stroke) path.alpha else 1f,
        |            strokeLineWidth = if (path.stroke) 2f else 0f,
        |            strokeLineCap = if (path.stroke) StrokeCap.Round else StrokeCap.Butt,
        |            strokeLineJoin = if (path.stroke) StrokeJoin.Round else StrokeJoin.Miter,
        |        )
        |    }
        |    return builder.build()
        |}
        |
        |/** One path as the generator wrote it: its data, and how it is drawn. */
        |private class TablerPath(encoded: String) {
        |    val data: String
        |    var fill: Boolean = ${style.fills}
        |    var stroke: Boolean = ${style.strokes}
        |    var evenOdd: Boolean = false
        |    var alpha: Float = 1f
        |
        |    init {
        |        if (encoded.startsWith('@')) {
        |            val end = encoded.indexOf(';')
        |            val flags = encoded.substring(1, end)
        |            data = encoded.substring(end + 1)
        |            fill = 'f' in flags
        |            stroke = 's' in flags
        |            evenOdd = 'e' in flags
        |            val a = flags.indexOf('a')
        |            if (a >= 0) alpha = flags.substring(a + 1).toFloat()
        |        } else {
        |            data = encoded
        |        }
        |    }
        |}
        |
        |private val Black = SolidColor(Color(0xFF000000))
        |
        |private val Primer = listOf(PathNode.MoveTo(0f, 0f))
        |""".trimMargin()
}
