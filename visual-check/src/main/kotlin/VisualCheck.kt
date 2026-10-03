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
import kotlin.math.min
import kotlin.system.exitProcess

/**
 * Every icon, drawn by Compose Desktop, against the same icon from Tabler's SVG
 * drawn by Chromium (`render-svgs.cjs`). See `run.sh`.
 *
 *     VisualCheck <work dir> <curated list> [icons to check]
 *
 * With no third argument, every icon Chromium drew; with one, the `style/name`
 * lines in it, which is how a release checks only what Tabler added or redrew
 * (`.github/scripts/visual-scope.sh`).
 *
 * Both are 96×96 (24 dp at 4×), black on transparent, and both renderers are
 * Skia, so a correct icon differs only by anti-aliasing. Each is scored on its
 * alpha channel, and fails on either:
 *
 *  - **mean** difference of 1% or more;
 *  - **wrong** pixels over 0.2% of the icon: those that differ by more than a
 *    quarter from every pixel within one pixel of them in the other drawing.
 *    The tolerance absorbs edges that land half a pixel apart in two
 *    renderers; a spike or a missing cap is several pixels long and stays.
 *
 * Calibrated on 3.48.0. With the primer, the worst icon has 0.11% wrong
 * pixels (`clock-exclamation`'s one-pixel dot). Without it — the round-join
 * bug — 573 icons have more than 0.2%, `sparkles` 2.7%. The margin matters
 * because CI draws Tabler's SVGs with the runner's Chrome, whose Skia is not
 * skiko's.
 *
 * Controls, which must fail, so that a pass means something: different icons
 * compared with each other, and real icons drawn with the wrong stroke.
 */

const val SIZE = 96

data class Score(val style: String, val name: String, val mean: Double, val wrong: Double) {
    val pass get() = mean < 0.01 && wrong <= 0.002
}

/** The icons the controls draw, which `run.sh` always has Chromium render. */
val CONTROLS = listOf(
    "outline/arrow-up", "outline/arrow-down", "outline/chevron-left", "outline/chevron-right",
    "outline/circle", "outline/circle-dot", "filled/star", "filled/heart",
    "outline/x", "outline/check", "outline/chevron-down", "outline/home",
)

fun main(args: Array<String>) {
    val work = File(args[0])
    val curated = File(args[1]).readLines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('/') }
    val only = args.getOrNull(2)?.let { File(it).readLines().filter(String::isNotBlank).map { line -> line.split('/') } }
    val tabler = work.resolve("tabler")
    val ours = work.resolve("ours")

    val icons = only ?: listOf("outline", "filled").flatMap { style ->
        tabler.resolve(style).list()!!.map { listOf(style, it.removeSuffix(".png")) }.sortedBy { it[1] }
    }
    val scores = icons.map { (style, name) -> compare(style, name, ImageIO.read(tabler.resolve("$style/$name.png")), ourImage(ours, style, name)) }

    work.resolve("report.tsv").writeText(
        "style\tname\tmean\twrong\tpass\n" +
            scores.joinToString("") { "${it.style}\t${it.name}\t%.5f\t%.5f\t${it.pass}\n".format(it.mean, it.wrong) }
    )
    val summary = StringBuilder()
    summary.appendLine("### Visual check: ${if (only == null) "every icon" else "${icons.size} icons"}")
    summary.appendLine()
    summary.appendLine("| Set | Checked | Look like Tabler's | Don't | Worst mean | Worst wrong pixels |")
    summary.appendLine("| --- | ---: | ---: | ---: | ---: | ---: |")
    for (style in listOf("outline", "filled")) {
        val s = scores.filter { it.style == style }
        if (s.isEmpty()) continue
        val line = "%-8s %4d icons: %4d look like Tabler's, %d do not | worst mean %.3f%% | worst wrong pixels %.3f%%"
            .format(style, s.size, s.count { it.pass }, s.count { !it.pass }, s.maxOf { it.mean } * 100, s.maxOf { it.wrong } * 100)
        println(line)
        summary.appendLine("| $style | ${s.size} | ${s.count { it.pass }} | ${s.count { !it.pass }} | %.3f%% | %.3f%% |"
            .format(s.maxOf { it.mean } * 100, s.maxOf { it.wrong } * 100))
    }
    val failures = scores.filter { !it.pass }.sortedByDescending { it.wrong }
    if (failures.isNotEmpty()) summary.appendLine().appendLine("**Differ from Tabler's:**")
    failures.take(25).forEach {
        val line = "${it.style}/${it.name}: mean %.3f%%, wrong pixels %.3f%%".format(it.mean * 100, it.wrong * 100)
        println("  differs: $line")
        summary.appendLine("- `$line`")
    }

    println("Controls, each of which must differ:")
    summary.appendLine().appendLine("**Controls**, each of which must differ:")
    var controlsHeld = true
    fun control(label: String, score: Score) {
        if (score.pass) controlsHeld = false
        val line = "${if (score.pass) "PASSED, so the check is blind" else "differs"}: $label (wrong pixels %.2f%%)".format(score.wrong * 100)
        println("  $line")
        summary.appendLine("- $line")
    }
    listOf("outline" to ("arrow-up" to "arrow-down"), "outline" to ("chevron-left" to "chevron-right"),
        "outline" to ("circle" to "circle-dot"), "filled" to ("star" to "heart")).forEach { (style, pair) ->
        val (a, b) = pair
        control("Tabler's $style $a against our $b",
            compare(style, a, ImageIO.read(tabler.resolve("$style/$a.png")), ourImage(ours, style, b)))
    }
    listOf("x", "check", "chevron-down", "home").forEach { name ->
        control("$name with a 1.5 stroke and butt caps",
            compare("outline", name, ImageIO.read(tabler.resolve("outline/$name.png")), render(wrongStroke(load("outline", name)))))
    }

    // Every icon: the curated ones and the 12 worst. A subset: those, worst first.
    val rows = if (only == null) {
        (curated + scores.sortedByDescending { it.wrong }.take(12).map { listOf(it.style, it.name) }).distinct()
    } else {
        scores.sortedByDescending { it.wrong }.take(90).map { listOf(it.style, it.name) }
    }
    sheet(rows, scores.associateBy { "${it.style}/${it.name}" }, tabler, ours, work.resolve("contact-sheet.png"))
    println("Contact sheet: ${work.resolve("contact-sheet.png")}")

    System.getenv("GITHUB_STEP_SUMMARY")?.let { File(it).appendText(summary.toString()) }
    if (failures.isNotEmpty() || !controlsHeld) exitProcess(1)
}

/** Ours, drawn once and kept as a PNG next to Tabler's. */
fun ourImage(ours: File, style: String, name: String): BufferedImage {
    val file = ours.resolve("$style/$name.png")
    if (file.exists()) return ImageIO.read(file)
    file.parentFile.mkdirs()
    return render(load(style, name)).also { ImageIO.write(it, "png", file) }
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
    val pa = IntArray(SIZE * SIZE) { alpha(a, it % SIZE, it / SIZE) }
    val pb = IntArray(SIZE * SIZE) { alpha(b, it % SIZE, it / SIZE) }
    // How far `p` in one drawing is from the closest match within a pixel of it in the other.
    fun nearest(from: IntArray, to: IntArray, x: Int, y: Int): Int {
        var best = 255
        for (dy in -1..1) for (dx in -1..1) {
            val qx = x + dx
            val qy = y + dy
            if (qx in 0 until SIZE && qy in 0 until SIZE) best = min(best, abs(from[y * SIZE + x] - to[qy * SIZE + qx]))
        }
        return best
    }
    var sum = 0L
    var wrong = 0
    for (y in 0 until SIZE) for (x in 0 until SIZE) {
        sum += abs(pa[y * SIZE + x] - pb[y * SIZE + x])
        if (max(nearest(pa, pb, x, y), nearest(pb, pa, x, y)) > 64) wrong++
    }
    val n = SIZE * SIZE
    return Score(style, name, sum / (255.0 * n), wrong.toDouble() / n)
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
    g.drawString("96×96 px (24 dp at 4×). mean: average alpha difference. wrong: pixels more than 25% off every pixel within 1 px of them. Fails at mean 1% or wrong 0.2%.", 12, 50)

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
        g.drawString("wrong %.2f%%".format(s.wrong * 100), bx + 12, y + 82)
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
