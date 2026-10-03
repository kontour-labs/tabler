import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.kontour.icons.tabler.Tabler
import org.jetbrains.skia.EncodedImageFormat
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.system.exitProcess

/**
 * Every icon, drawn by Compose Desktop, against the same icon from Tabler's SVG
 * drawn by Chromium (`render-svgs.cjs`). See `run.sh`.
 *
 * Both are 96×96 (24 dp at 4×), black on transparent, and both renderers are
 * Skia, so a correct icon differs only by anti-aliasing. Each is scored on its
 * alpha channel:
 *
 *  - mean difference, which must be under 1%;
 *  - the share of pixels that differ by more than a quarter, which must be under
 *    0.5%. A spike or a missing cap is a few dozen pixels that are entirely
 *    wrong, which is what this catches and an average hides.
 *
 * Controls, which must fail, so that a pass means something: different icons
 * compared with each other, and real icons drawn with the wrong stroke.
 *
 * This is how the round-join bug was found: Compose's Skia renderer drew 297
 * outline icons with mitred corners until `IconFile` added the primer.
 */

const val SIZE = 96

data class Score(val style: String, val name: String, val mean: Double, val max: Int, val over: Double) {
    val pass get() = mean < 0.01 && over < 0.005
}

fun main(args: Array<String>) {
    val work = File(args[0])
    val curated = File(args[1]).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('/') }
    val tabler = work.resolve("tabler")
    val ours = work.resolve("ours")

    val scores = mutableListOf<Score>()
    for (style in listOf("outline", "filled")) {
        val names = tabler.resolve(style).list()!!.map { it.removeSuffix(".png") }.sorted()
        ours.resolve(style).mkdirs()
        for (name in names) {
            val mine = render(load(style, name))
            ImageIO.write(mine, "png", ours.resolve("$style/$name.png"))
            scores += compare(style, name, ImageIO.read(tabler.resolve("$style/$name.png")), mine)
        }
    }

    work.resolve("report.tsv").writeText(
        "style\tname\tmean\tmax\tover\tpass\n" +
            scores.joinToString("") { "${it.style}\t${it.name}\t%.5f\t${it.max}\t%.5f\t${it.pass}\n".format(it.mean, it.over) }
    )
    for (style in listOf("outline", "filled")) {
        val s = scores.filter { it.style == style }
        println(
            "%-8s %4d icons: %4d look like Tabler's, %d do not | worst mean %.3f%% | worst share over 25%%: %.3f%%"
                .format(style, s.size, s.count { it.pass }, s.count { !it.pass }, s.maxOf { it.mean } * 100, s.maxOf { it.over } * 100)
        )
    }
    val failures = scores.filter { !it.pass }
    failures.take(25).forEach { println("  differs: ${it.style}/${it.name}, mean %.3f%%, over %.3f%%".format(it.mean * 100, it.over * 100)) }

    println("Controls, each of which must differ:")
    var controlsHeld = true
    fun control(label: String, score: Score) {
        if (score.pass) controlsHeld = false
        println("  ${if (score.pass) "PASSED, so the check is blind" else "differs"}: $label (mean %.2f%%)".format(score.mean * 100))
    }
    listOf("outline" to ("arrow-up" to "arrow-down"), "outline" to ("chevron-left" to "chevron-right"),
        "outline" to ("circle" to "circle-dot"), "filled" to ("star" to "heart")).forEach { (style, pair) ->
        val (a, b) = pair
        control("Tabler's $style $a against our $b",
            compare(style, a, ImageIO.read(tabler.resolve("$style/$a.png")), ImageIO.read(ours.resolve("$style/$b.png"))))
    }
    listOf("x", "check", "chevron-down", "home").forEach { name ->
        control("$name with a 1.5 stroke and butt caps",
            compare("outline", name, ImageIO.read(tabler.resolve("outline/$name.png")), render(wrongStroke(load("outline", name)))))
    }

    val worst = scores.sortedByDescending { it.mean }.take(12).map { listOf(it.style, it.name) }
    sheet((curated + worst).distinct(), scores.associateBy { "${it.style}/${it.name}" }, tabler, ours, work.resolve("contact-sheet.png"))
    println("Contact sheet: ${work.resolve("contact-sheet.png")}")

    if (failures.isNotEmpty() || !controlsHeld) exitProcess(1)
}

/** `Tabler.Outline.Abc` is `AbcKt.getAbc(Tabler.Outline)` to the JVM. */
fun load(style: String, name: String): ImageVector {
    val receiver: Any = if (style == "outline") Tabler.Outline else Tabler.Filled
    val property = name.split('-').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    return Class.forName("io.kontour.icons.tabler.$style.${property}Kt")
        .getMethod("get$property", receiver.javaClass).invoke(null, receiver) as ImageVector
}

/** As an app's `Icon` draws it: through `rememberVectorPainter`, 24 dp at density 4. */
fun render(vector: ImageVector): BufferedImage {
    val scene = ImageComposeScene(SIZE, SIZE, Density(4f)) {
        Box(Modifier.size(24.dp).paint(rememberVectorPainter(vector)))
    }
    try {
        return ImageIO.read(ByteArrayInputStream(scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes))
    } finally {
        scene.close()
    }
}

fun alpha(image: BufferedImage, x: Int, y: Int) = (image.getRGB(x, y) ushr 24) and 0xFF

fun compare(style: String, name: String, a: BufferedImage, b: BufferedImage): Score {
    var sum = 0L
    var worst = 0
    var over = 0
    for (y in 0 until SIZE) for (x in 0 until SIZE) {
        val d = abs(alpha(a, x, y) - alpha(b, x, y))
        sum += d
        worst = max(worst, d)
        if (d > 64) over++
    }
    val n = SIZE * SIZE
    return Score(style, name, sum / (255.0 * n), worst, over.toDouble() / n)
}

/** The same icon, thinner and with butt caps. */
fun wrongStroke(vector: ImageVector): ImageVector =
    ImageVector.Builder(vector.name, 24.dp, 24.dp, 24f, 24f).apply {
        vector.root.forEach { node ->
            val path = node as VectorPath
            addPath(
                pathData = path.pathData,
                fill = path.fill,
                stroke = path.stroke,
                strokeLineWidth = if (path.strokeLineWidth == 2f) 1.5f else path.strokeLineWidth,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = path.strokeLineJoin,
            )
        }
    }.build()

/** Rows of `name | Tabler's | ours | difference`, three blocks across. */
fun sheet(rows: List<List<String>>, scores: Map<String, Score>, tabler: File, ours: File, out: File) {
    val blocks = 3
    val label = 200
    val cell = SIZE + 12
    val blockWidth = label + cell * 3 + 24
    val perBlock = (rows.size + blocks - 1) / blocks
    val header = 64
    val image = BufferedImage(blockWidth * blocks, header + perBlock * cell + 16, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color.WHITE
    g.fillRect(0, 0, image.width, image.height)
    g.color = Color.BLACK
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 18)
    g.drawString("Tabler's SVG (Chromium)  |  io.kontour ImageVector (Compose Desktop)  |  difference: red only ours, blue only Tabler's", 12, 28)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
    g.drawString("96×96 px (24 dp at 4×). mean: average alpha difference. over: pixels differing by more than 25%. The last rows are the 12 worst of every icon.", 12, 50)

    rows.forEachIndexed { index, (style, name) ->
        val bx = (index / perBlock) * blockWidth
        val y = header + (index % perBlock) * cell
        val a = ImageIO.read(tabler.resolve("$style/$name.png"))
        val b = ImageIO.read(ours.resolve("$style/$name.png"))
        val diff = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB)
        for (py in 0 until SIZE) for (px in 0 until SIZE) {
            val d = alpha(b, px, py) - alpha(a, px, py)
            val ghost = 255 - maxOf(alpha(a, px, py), alpha(b, px, py)) / 8
            diff.setRGB(px, py, when {
                d > 0 -> Color(255, 255 - d, 255 - d).rgb
                d < 0 -> Color(255 + d, 255 + d, 255).rgb
                else -> Color(ghost, ghost, ghost).rgb
            })
        }
        val s = scores.getValue("$style/$name")
        g.color = if (s.pass) Color(0, 110, 40) else Color(180, 0, 0)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 13)
        g.drawString(name, bx + 12, y + 30)
        g.color = Color.DARK_GRAY
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        g.drawString(style, bx + 12, y + 48)
        g.drawString("mean %.2f%%".format(s.mean * 100), bx + 12, y + 66)
        g.drawString("over %.2f%%".format(s.over * 100), bx + 12, y + 82)
        listOf(a, b, diff).forEachIndexed { i, img ->
            val x = bx + label + i * cell
            g.color = Color(0xEE, 0xEE, 0xEE)
            g.drawRect(x - 1, y - 1, SIZE + 1, SIZE + 1)
            g.drawImage(img, x, y, null)
        }
    }
    g.dispose()
    ImageIO.write(image, "png", out)
}
