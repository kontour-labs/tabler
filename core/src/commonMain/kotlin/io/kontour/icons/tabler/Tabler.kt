package io.kontour.icons.tabler

/**
 * [Tabler Icons](https://tabler.io/icons), as Compose `ImageVector`s.
 *
 * Each icon is an extension property on one of the two sets, named after Tabler's
 * own name in PascalCase — `a-b-2` is `AB2`:
 *
 * ```
 * import io.kontour.icons.tabler.Tabler
 * import io.kontour.icons.tabler.outline.Abc
 * import io.kontour.icons.tabler.filled.Star
 *
 * Icon(Tabler.Outline.Abc, contentDescription = null)
 * Icon(Tabler.Filled.Star, contentDescription = null)
 * ```
 *
 * The sets are separate artifacts, `tabler-icons-outline` and
 * `tabler-icons-filled`; this one only holds the objects they extend.
 */
public object Tabler {
    /** The stroked set, `io.kontour.icons.tabler.outline`. Every Tabler icon has one. */
    public object Outline

    /** The solid set, `io.kontour.icons.tabler.filled`. About a fifth of the icons have one. */
    public object Filled
}
