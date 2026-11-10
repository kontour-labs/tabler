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
        assertTrue("tablerIcon(\"star\", \"M0 0h1z\")" in star, star)

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
        // Each path that isn't drawn the set's way says how, ahead of its data.
        assertEquals(listOf("@f;M0 0h1", "@sa0.5;M0 0h1", "@fse;M0 0h1"), mixed.paths.map { IconFile.encode(it, Style.Outline) })
        val text = IconFile.build(mixed, Style.Outline, "p").toString()
        assertTrue("tablerIcon(\"mixed\", \"@f;M0 0h1\", \"@sa0.5;M0 0h1\", \"@fse;M0 0h1\")" in text, text)
    }

    @Test
    fun `each set's helper draws a path the set's way unless it says otherwise`() {
        val outline = IconFile.helper(Style.Outline, "io.kontour.icons.tabler")
        assertTrue("package io.kontour.icons.tabler.outline" in outline, outline)
        assertTrue("var fill: Boolean = false" in outline && "var stroke: Boolean = true" in outline, outline)
        assertTrue("internal fun tablerIcon(name: String, vararg paths: String): ImageVector" in outline, outline)
        val filled = IconFile.helper(Style.Filled, "io.kontour.icons.tabler")
        assertTrue("package io.kontour.icons.tabler.filled" in filled, filled)
        assertTrue("var fill: Boolean = true" in filled && "var stroke: Boolean = false" in filled, filled)
        // A set's defaults are unprefixed: a filled path in filled is its data alone.
        assertEquals("M0 0h1z", IconFile.encode(IconPath("M0 0h1z", fill = true, stroke = false, alpha = null, evenOdd = false), Style.Filled))
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
 * Composables' `Abc`, under this package: a getter that builds it once and keeps
 * it, from Tabler's own path data. The set's `tablerIcon` does the drawing.
 */
private val ABC = """package io.kontour.icons.tabler.outline

import androidx.compose.ui.graphics.vector.ImageVector
import io.kontour.icons.tabler.Tabler

public val Tabler.Outline.Abc: ImageVector
    get() = _Abc ?: tablerIcon("abc", "M3 16v-6a2 2 0 1 1 4 0v6", "M3 13h4", "M10 8v6a2 2 0 1 0 4 0v-1a2 2 0 1 0 -4 0v1", "M20.732 12a2 2 0 0 0 -3.732 1v1a2 2 0 0 0 3.726 1.01").also { _Abc = it }

private var _Abc: ImageVector? = null
"""
