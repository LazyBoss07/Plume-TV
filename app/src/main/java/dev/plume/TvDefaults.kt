package dev.plume

/**
 * This fork's own defaults, used until a setting is changed in Plume settings. Kept in one file so merges from
 * upstream Plume (aradhyas8/Plume_Launcher) stay clean: upstream code only reads these names.
 */
internal object TvDefaults {
    /** Wallpaper & theme > Theme: "light", "dark" or "auto". Upstream starts light. */
    const val THEME = "dark"

    /** Apps row > Icon style: one of ICON_STYLE_KEYS. Upstream starts monochrome. */
    const val ICON_STYLE = "colour"

    /** Apps row > Selected app size: one of MainActivity.LIFTS. Upstream's fixed lift was 1.08. */
    const val LIFT = "1.3"

    /** Where Updates looks for new builds: this fork's GitHub releases (set by the build, see app/build.gradle.kts). */
    const val UPDATE_REPO = BuildConfig.UPDATE_REPO
}
