package tabler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IconFileTest {

    private fun path(d: String, vararg attributes: Pair<String, String>) = listOf("path", mapOf("d" to d) + attributes)

    /** Tabler's `abc`, as `tabler-nodes-outline.json` has it. */
    private val abc = listOf(
        path("M3 16v-6a2 2 0 1 1 4 0v6"),
        path("M3 13h4"),
        path("M10 8v6a2 2 0 1 0 4 0v-1a2 2 0 1 0 -4 0v1"),
        path("M20.732 12a2 2 0 0 0 -3.732 1v1a2 2 0 0 0 3.726 1.01"),
    )

    @Test
    fun `an outline icon comes out shaped like composables' own`() {
        val icon = Icons.fromNodes("abc", abc, Style.Outline)
        assertEquals(ABC, IconFile.build(icon, Style.Outline, "io.kontour.icons.tabler").toString())
    }

    @Test
    fun `filled paths fill, and attributes override the style's defaults`() {
        val star = IconFile.build(Icons.fromNodes("star", listOf(path("M0 0h1z")), Style.Filled), Style.Filled, "p").toString()
        assertTrue("public val Tabler.Filled.Star: ImageVector" in star, star)
        assertTrue("fill = SolidColor(Color(0xFF000000))\n" in star, star)
        assertTrue("stroke" !in star, star)

        val mixed = Icons.fromNodes(
            "mixed",
            listOf(
                path("M0 0h1", "fill" to "currentColor", "stroke" to "none"),
                path("M0 0h1", "opacity" to ".5"),
                path("M0 0h1", "fill" to "currentColor", "fill-rule" to "evenodd"),
                // The bounding box, drawn by neither: dropped.
                path("M0 0h24v24H0z", "stroke" to "none", "fill" to "none"),
            ),
            Style.Outline,
        )
        assertEquals(
            listOf(
                IconPath("M0 0h1", fill = true, stroke = false, alpha = null, evenOdd = false),
                IconPath("M0 0h1", fill = false, stroke = true, alpha = "0.5f", evenOdd = false),
                IconPath("M0 0h1", fill = true, stroke = true, alpha = null, evenOdd = true),
            ),
            mixed.paths,
        )
        val text = IconFile.build(mixed, Style.Outline, "p").toString()
        assertTrue("strokeAlpha = 0.5f," in text, text)
        assertTrue("pathFillType = PathFillType.EvenOdd" in text, text)
    }

    @Test
    fun `names are composables' names`() {
        assertEquals("Abc", Icons.propertyName("abc"))
        assertEquals("AB2", Icons.propertyName("a-b-2"))
        assertEquals("Brand4chan", Icons.propertyName("brand-4chan"))
        assertEquals("ArrowBigUpLine", Icons.propertyName("arrow-big-up-line"))
        assertFailsWith<IllegalArgumentException> { Icons.propertyName("2fa") }
    }

    @Test
    fun `anything the generator does not understand fails, naming the icon`() {
        listOf(
            listOf(listOf("circle", mapOf("cx" to "12"))),
            listOf(path("M0 0h1", "stroke-width" to "1")),
            listOf(path("M0 0h1", "fill" to "red")),
            listOf(path("M0 0x1")),
            listOf(path("M0 0h24v24H0z", "stroke" to "none")),
        ).forEach { nodes ->
            val error = assertFailsWith<IllegalArgumentException>(nodes.toString()) { Icons.fromNodes("bad-icon", nodes, Style.Outline) }
            assertTrue("bad-icon" in error.message.orEmpty(), error.message)
        }
    }
}

/**
 * The user's example of composables' `Abc`, under this package, without the
 * transparent fill, and with the primer that makes Compose honour round joins.
 */
private val ABC = """package io.kontour.icons.tabler.outline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler

public val Tabler.Outline.Abc: ImageVector
    get() {
        if (_Abc != null) return _Abc!!

        _Abc = ImageVector.Builder(
            name = "abc",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Draws nothing. It makes Compose's Skia renderer honour the round joins below.
            path(
                stroke = SolidColor(Color.Transparent),
                strokeLineWidth = 1f,
                strokeLineJoin = StrokeJoin.Miter
            ) {
                moveTo(0f, 0f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 16f)
                verticalLineToRelative(-6f)
                arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)
                verticalLineToRelative(6f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(3f, 13f)
                horizontalLineToRelative(4f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 8f)
                verticalLineToRelative(6f)
                arcToRelative(2f, 2f, 0f, true, false, 4f, 0f)
                verticalLineToRelative(-1f)
                arcToRelative(2f, 2f, 0f, true, false, -4f, 0f)
                verticalLineToRelative(1f)
            }
            path(
                stroke = SolidColor(Color(0xFF000000)),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(20.732f, 12f)
                arcToRelative(2f, 2f, 0f, false, false, -3.732f, 1f)
                verticalLineToRelative(1f)
                arcToRelative(2f, 2f, 0f, false, false, 3.726f, 1.01f)
            }
        }.build()

        return _Abc!!
    }

private var _Abc: ImageVector? = null
"""
