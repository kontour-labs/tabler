package tabler

/** The two sets Tabler publishes, and how each draws a path by default. */
internal enum class Style(
    /** The npm package's name for it: `tabler-nodes-<id>.json`. */
    val id: String,
    /** The nested object in `Tabler`, and the last segment of the package. */
    val objectName: String,
    /** What the SVG root says: outline is `fill="none" stroke="currentColor"`… */
    val fills: Boolean,
    /** …and filled is `fill="currentColor"` with no stroke. */
    val strokes: Boolean,
) {
    Outline("outline", "Outline", fills = false, strokes = true),
    Filled("filled", "Filled", fills = true, strokes = false);

    companion object {
        fun of(id: String): Style = entries.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown Tabler style `$id`; expected one of ${entries.map { it.id }}")
    }
}

/** One `<path>`, with its root's defaults already applied. */
internal data class IconPath(
    val d: String,
    val fill: Boolean,
    val stroke: Boolean,
    /** `opacity`, as a Kotlin literal, or null for opaque. */
    val alpha: String?,
    val evenOdd: Boolean,
)

internal data class Icon(
    /** Tabler's own name, `a-b-2`. It is also the vector's `name`. */
    val name: String,
    /** The Kotlin property, `AB2`. */
    val property: String,
    val paths: List<IconPath>,
)

internal object Icons {

    /**
     * `a-b-2` → `AB2`, `brand-4chan` → `Brand4chan`: each part capitalised, as
     * composables' icons are, so a migration is a change of import only.
     */
    fun propertyName(name: String): String {
        val property = name.split('-').joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        require(Regex("[A-Z][A-Za-z0-9]*").matches(property)) {
            "Tabler icon `$name` does not make a Kotlin identifier (got `$property`)."
        }
        return property
    }

    /**
     * One icon from `tabler-nodes-<style>.json`, where each is a list of
     * `[element, attributes]` pairs.
     *
     * Tabler normalises these before publishing them — every circle, line and
     * rectangle is already a path — so anything else is a change to that format
     * and fails here, by name, rather than shipping as an icon drawn wrong.
     */
    fun fromNodes(name: String, nodes: List<*>, style: Style): Icon {
        fun fail(problem: String): Nothing =
            throw IllegalArgumentException("Tabler icon `$name` (${style.id}): $problem")

        val paths = nodes.mapNotNull { node ->
            val pair = node as? List<*> ?: fail("a node that is not an [element, attributes] pair: $node")
            val element = pair.getOrNull(0)
            val attributes = pair.getOrNull(1) as? Map<*, *> ?: fail("a node with no attributes: $node")
            if (element != "path") fail("a `<$element>`; only paths are supported")

            var d: String? = null
            var fill = style.fills
            var stroke = style.strokes
            var alpha: String? = null
            var evenOdd = false
            for ((key, value) in attributes) {
                val text = value.toString()
                when (key) {
                    "d" -> d = text
                    "fill" -> fill = paint(text) ?: fail("fill=\"$text\"")
                    "stroke" -> stroke = paint(text) ?: fail("stroke=\"$text\"")
                    "opacity" -> alpha = runCatching { PathData.floatLiteral(text) }.getOrNull()
                        ?.takeIf { text.toFloat() in 0f..1f } ?: fail("opacity=\"$text\"")
                    "fill-rule" -> evenOdd = when (text) {
                        "evenodd" -> true
                        "nonzero" -> false
                        else -> fail("fill-rule=\"$text\"")
                    }
                    else -> fail("an attribute this generator does not understand: $key=\"$text\"")
                }
            }
            if (d.isNullOrBlank()) fail("a path with no `d`")
            // Neither filled nor stroked draws nothing: the 24×24 bounding box
            // every Tabler SVG carries, should it ever appear in this data.
            if (!fill && !stroke) return@mapNotNull null
            runCatching { PathData.parse(d) }.onFailure { fail(it.message ?: "bad path data") }
            IconPath(d, fill, stroke, alpha, evenOdd)
        }
        if (paths.isEmpty()) fail("no visible paths")
        return Icon(name, propertyName(name), paths)
    }

    private fun paint(value: String): Boolean? = when (value) {
        "currentColor" -> true
        "none" -> false
        else -> null
    }
}
