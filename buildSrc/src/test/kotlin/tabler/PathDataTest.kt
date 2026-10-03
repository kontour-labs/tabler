package tabler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PathDataTest {

    private fun calls(d: String) = PathData.parse(d).map { "${it.function}(${it.args.joinToString(", ")})" }

    @Test
    fun `reads the commands Tabler writes`() {
        assertEquals(
            listOf(
                "moveTo(3f, 16f)",
                "verticalLineToRelative(-6f)",
                "arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)",
                "verticalLineToRelative(6f)",
            ),
            calls("M3 16v-6a2 2 0 1 1 4 0v6"),
        )
    }

    @Test
    fun `numbers run together where the second cannot continue the first`() {
        assertEquals(listOf("moveTo(0.5f, 0.5f)", "lineToRelative(-0.5f, -0.5f)"), calls("M.5.5l-.5-.5"))
        assertEquals(listOf("moveTo(1f, -2f)"), calls("M1-2"))
    }

    @Test
    fun `arc flags need no separator`() {
        assertEquals(listOf("moveTo(0f, 0f)", "arcToRelative(2f, 2f, 0f, true, true, 4f, 0f)"), calls("M0 0a2 2 0 114 0"))
        assertEquals(listOf("moveTo(0f, 0f)", "arcTo(2f, 2f, 0f, false, true, -4f, 0.5f)"), calls("M0,0A2,2,0,0,1,-4,.5"))
    }

    @Test
    fun `further argument sets repeat the command, and after a move they are lines`() {
        assertEquals(
            listOf("moveTo(1f, 2f)", "lineTo(3f, 4f)", "lineTo(5f, 6f)", "moveToRelative(1f, 1f)", "lineToRelative(2f, 2f)"),
            calls("M1 2 3 4 5 6m1 1 2 2"),
        )
        assertEquals(listOf("moveTo(0f, 0f)", "horizontalLineToRelative(1f)", "horizontalLineToRelative(2f)"), calls("M0 0h1 2"))
        assertEquals(
            listOf("moveTo(0f, 0f)", "curveToRelative(1f, 1f, 2f, 2f, 3f, 3f)", "curveToRelative(4f, 4f, 5f, 5f, 6f, 6f)"),
            calls("M0 0c1 1 2 2 3 3 4 4 5 5 6 6"),
        )
    }

    @Test
    fun `every command maps to its builder call`() {
        assertEquals(
            listOf(
                "moveTo(0f, 0f)", "lineTo(1f, 1f)", "horizontalLineTo(2f)", "verticalLineTo(3f)",
                "curveTo(1f, 2f, 3f, 4f, 5f, 6f)", "reflectiveCurveTo(1f, 2f, 3f, 4f)",
                "quadTo(1f, 2f, 3f, 4f)", "reflectiveQuadTo(1f, 2f)", "close()",
                "moveToRelative(1f, 1f)", "quadToRelative(1f, 2f, 3f, 4f)", "reflectiveQuadToRelative(1f, 2f)",
                "reflectiveCurveToRelative(1f, 2f, 3f, 4f)", "close()",
            ),
            calls("M0 0L1 1H2V3C1 2 3 4 5 6S1 2 3 4Q1 2 3 4T1 2Zm1 1q1 2 3 4t1 2s1 2 3 4z"),
        )
    }

    @Test
    fun `a close may be followed directly by a command`() {
        assertEquals(listOf("moveTo(0f, 0f)", "close()", "moveToRelative(1f, 1f)"), calls("M0 0zm1 1"))
    }

    @Test
    fun `numbers become Kotlin float literals`() {
        assertEquals("-0.5f", PathData.floatLiteral("-.5"))
        assertEquals("16f", PathData.floatLiteral("16"))
        assertEquals("10f", PathData.floatLiteral("10.0"))
        assertEquals("0.001f", PathData.floatLiteral("1e-3"))
        assertEquals("0f", PathData.floatLiteral("-0"))
        assertEquals("1.48f", PathData.floatLiteral("1.480"))
        assertEquals(listOf("moveTo(100f, 0.001f)"), calls("M1e2 1E-3"))
    }

    @Test
    fun `anything else fails, saying where`() {
        listOf("M0 0x1", "M0 0a2 2 0 2 1 4 0", "M0", "1 2", "M0 0z 1 2", "M0 0l.").forEach { bad ->
            val error = assertFailsWith<IllegalArgumentException>(bad) { PathData.parse(bad) }
            assertTrue("⟨here⟩" in error.message.orEmpty() || "move" in error.message.orEmpty(), error.message)
        }
        assertFailsWith<IllegalArgumentException> { PathData.parse("L1 1") }
    }
}
