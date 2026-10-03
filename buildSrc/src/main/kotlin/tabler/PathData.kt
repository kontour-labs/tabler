package tabler

import java.math.BigDecimal

/**
 * One call on Compose's `PathBuilder`: `moveTo(3f, 16f)`, `arcToRelative(…)`,
 * `close()`. [args] are Kotlin literals, ready to print: `3f`, `-0.5f`, `true`.
 */
internal data class PathCommand(val function: String, val args: List<String>)

/**
 * SVG path data (`d="M3 16v-6a2 2 0 1 1 4 0v6"`) as the `PathBuilder` calls that
 * draw the same thing.
 *
 * The whole grammar, not just the subset Tabler happens to write today, because
 * the release workflow runs no tests: what this accepts is what ships. Hence
 * also the failures, which name the offending character rather than guessing.
 * The parts of the grammar that are easy to get wrong, each covered in
 * `PathDataTest`:
 *
 *  - numbers run together whenever the second cannot continue the first:
 *    `.5.5` is two numbers, and so is `1-2`;
 *  - an arc's two flags are single characters and need no separator, so
 *    `a2 2 0 114 0` is `2 2 0 1 1 4 0`;
 *  - a command letter may be followed by several sets of arguments, which
 *    repeat it, except that the sets after a `M`/`m` are `L`/`l`.
 *
 * Compose parses the same strings at runtime with `PathParser`; the equivalence
 * test in `test-support/` holds every generated icon to what that parser makes of
 * Tabler's original data.
 */
internal object PathData {

    /** A command's builder function, and its arguments: `n` a number, `f` a flag. */
    private class Spec(val function: String, val kinds: String)

    private val specs: Map<Char, Spec> = mapOf(
        'M' to Spec("moveTo", "nn"),
        'm' to Spec("moveToRelative", "nn"),
        'L' to Spec("lineTo", "nn"),
        'l' to Spec("lineToRelative", "nn"),
        'H' to Spec("horizontalLineTo", "n"),
        'h' to Spec("horizontalLineToRelative", "n"),
        'V' to Spec("verticalLineTo", "n"),
        'v' to Spec("verticalLineToRelative", "n"),
        'C' to Spec("curveTo", "nnnnnn"),
        'c' to Spec("curveToRelative", "nnnnnn"),
        'S' to Spec("reflectiveCurveTo", "nnnn"),
        's' to Spec("reflectiveCurveToRelative", "nnnn"),
        'Q' to Spec("quadTo", "nnnn"),
        'q' to Spec("quadToRelative", "nnnn"),
        'T' to Spec("reflectiveQuadTo", "nn"),
        't' to Spec("reflectiveQuadToRelative", "nn"),
        'A' to Spec("arcTo", "nnnffnn"),
        'a' to Spec("arcToRelative", "nnnffnn"),
        'Z' to Spec("close", ""),
        'z' to Spec("close", ""),
    )

    fun parse(d: String): List<PathCommand> {
        val reader = Reader(d)
        val commands = mutableListOf<PathCommand>()
        var current: Char? = null

        while (true) {
            reader.skipSeparators()
            if (reader.atEnd) break

            if (reader.peek().isLetter()) {
                val letter = reader.next()
                val spec = specs[letter] ?: reader.fail("unknown command `$letter`")
                if (spec.kinds.isEmpty()) {
                    commands += PathCommand(spec.function, emptyList())
                    current = null
                    continue
                }
                commands += PathCommand(spec.function, reader.arguments(spec.kinds))
                // Further argument sets after a move are lines, not more moves.
                current = when (letter) {
                    'M' -> 'L'
                    'm' -> 'l'
                    else -> letter
                }
            } else {
                // A further set of arguments for the command before it.
                val letter = current ?: reader.fail("a number with no command to belong to")
                val spec = specs.getValue(letter)
                commands += PathCommand(spec.function, reader.arguments(spec.kinds))
            }
        }

        require(commands.isEmpty() || commands.first().function.startsWith("moveTo")) {
            "Path data must start with a move: `$d`"
        }
        return commands
    }

    private class Reader(private val text: String) {
        private var at = 0

        val atEnd: Boolean get() = at >= text.length

        fun peek(): Char = text[at]

        fun next(): Char = text[at++]

        fun skipSeparators() {
            while (!atEnd && (text[at].isWhitespace() || text[at] == ',')) at++
        }

        fun arguments(kinds: String): List<String> = kinds.map { kind ->
            skipSeparators()
            if (atEnd) fail("expected ${if (kind == 'f') "a flag" else "a number"}, found the end")
            if (kind == 'f') flag() else number()
        }

        private fun flag(): String = when (next()) {
            '0' -> "false"
            '1' -> "true"
            else -> {
                at--
                fail("an arc flag must be 0 or 1")
            }
        }

        // sign? (digits ('.' digits?)? | '.' digits) (('e' | 'E') sign? digits)?
        private fun number(): String {
            val start = at
            if (!atEnd && (text[at] == '+' || text[at] == '-')) at++
            val integer = digits()
            var fraction = 0
            if (!atEnd && text[at] == '.') {
                at++
                fraction = digits()
            }
            if (integer == 0 && fraction == 0) {
                at = start
                fail("expected a number")
            }
            if (!atEnd && (text[at] == 'e' || text[at] == 'E')) {
                val mark = at
                at++
                if (!atEnd && (text[at] == '+' || text[at] == '-')) at++
                // An `e` with no digits after it was never an exponent.
                if (digits() == 0) at = mark
            }
            return floatLiteral(text.substring(start, at))
        }

        private fun digits(): Int {
            val start = at
            while (!atEnd && text[at].isDigit()) at++
            return at - start
        }

        fun fail(problem: String): Nothing {
            val shown = text.substring(0, at) + "⟨here⟩" + text.substring(at)
            throw IllegalArgumentException("Bad path data, $problem: `$shown`")
        }
    }

    /** `-.5` → `-0.5f`, `16` → `16f`, `1e-3` → `0.001f`, `-0` → `0f`. */
    fun floatLiteral(number: String): String {
        val plain = BigDecimal(number).stripTrailingZeros().toPlainString()
        return (if (plain == "-0") "0" else plain) + "f"
    }
}
