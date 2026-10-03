package tabler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec

/**
 * The Kotlin file for one icon, shaped like composables' so that a migration is
 * a change of package and nothing else:
 *
 * ```
 * public val Tabler.Outline.Abc: ImageVector
 *     get() {
 *         if (_Abc != null) return _Abc!!
 *         _Abc = ImageVector.Builder(…).apply { path(…) { moveTo(3f, 16f) … } }.build()
 *         return _Abc!!
 *     }
 *
 * private var _Abc: ImageVector? = null
 * ```
 *
 * Built on first use and kept, so an app pays for the icons it draws and not for
 * the six thousand it does not.
 *
 * Every space in the code below is `·`, KotlinPoet's non-breaking space. Without
 * it a long `curveToRelative` is wrapped at column 100 wherever KotlinPoet
 * likes, which compiles but reads badly.
 *
 * Nothing here may depend on the Tabler version — not even a comment — because a
 * set Tabler left alone must come out byte for byte the same, which is what lets
 * the build cache skip compiling it.
 */
internal object IconFile {

    private val imageVector = ClassName("androidx.compose.ui.graphics.vector", "ImageVector")
    private val path = MemberName("androidx.compose.ui.graphics.vector", "path", isExtension = true)
    private val dp = MemberName("androidx.compose.ui.unit", "dp", isExtension = true)
    private val color = ClassName("androidx.compose.ui.graphics", "Color")
    private val solidColor = ClassName("androidx.compose.ui.graphics", "SolidColor")
    private val strokeCap = ClassName("androidx.compose.ui.graphics", "StrokeCap")
    private val strokeJoin = ClassName("androidx.compose.ui.graphics", "StrokeJoin")
    private val pathFillType = ClassName("androidx.compose.ui.graphics", "PathFillType")

    fun build(icon: Icon, style: Style, basePackage: String): FileSpec {
        val receiver = ClassName(basePackage, "Tabler", style.objectName)
        val backing = PropertySpec.builder("_${icon.property}", imageVector.copy(nullable = true), KModifier.PRIVATE)
            .mutable()
            .initializer("null")
            .build()

        val getter = FunSpec.getterBuilder().addCode(body(icon, backing)).build()
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

    private fun body(icon: Icon, backing: PropertySpec): CodeBlock = CodeBlock.builder().apply {
        add("if·(%N·!=·null)·return·%N!!\n", backing, backing)
        add("\n")
        add("%N·=·%T.Builder(\n", backing, imageVector)
        indent()
        add("name·=·%S,\n", icon.name)
        add("defaultWidth·=·24.%M,\n", dp)
        add("defaultHeight·=·24.%M,\n", dp)
        add("viewportWidth·=·24f,\n")
        add("viewportHeight·=·24f\n")
        unindent()
        add(").apply·{\n")
        indent()
        icon.paths.forEach { path(it) }
        unindent()
        add("}.build()\n")
        add("\n")
        add("return·%N!!\n", backing)
    }.build()

    private fun CodeBlock.Builder.path(path: IconPath) {
        val parameters = buildList {
            if (path.fill) add(CodeBlock.of("fill·=·%T(%T(0xFF000000))", solidColor, color))
            if (path.fill && path.alpha != null) add(CodeBlock.of("fillAlpha·=·%L", path.alpha))
            if (path.stroke) {
                add(CodeBlock.of("stroke·=·%T(%T(0xFF000000))", solidColor, color))
                if (path.alpha != null) add(CodeBlock.of("strokeAlpha·=·%L", path.alpha))
                add(CodeBlock.of("strokeLineWidth·=·2f"))
                add(CodeBlock.of("strokeLineCap·=·%T.Round", strokeCap))
                add(CodeBlock.of("strokeLineJoin·=·%T.Round", strokeJoin))
            }
            if (path.evenOdd) add(CodeBlock.of("pathFillType·=·%T.EvenOdd", pathFillType))
        }

        add("%M(\n", this@IconFile.path)
        indent()
        parameters.forEachIndexed { index, parameter ->
            add(parameter)
            add(if (index < parameters.lastIndex) ",\n" else "\n")
        }
        unindent()
        add(")·{\n")
        indent()
        for (command in PathData.parse(path.d)) {
            add("%L(%L)\n", command.function, command.args.joinToString(",·"))
        }
        unindent()
        add("}\n")
    }
}
