package org.politisationpopulairefrancaise.core

/**
 * Window size classes, following the Material Design breakpoints
 * (compact < 600 dp, medium < 840 dp, expanded otherwise).
 *
 * Computed here, in pure Kotlin, so that it has no dependency on any UI library
 * and can be unit-tested on every platform.
 */
enum class WindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        const val MEDIUM_MIN_DP: Float = 600f
        const val EXPANDED_MIN_DP: Float = 840f

        fun fromWidthDp(widthDp: Float): WindowWidthClass = when {
            widthDp < MEDIUM_MIN_DP -> COMPACT
            widthDp < EXPANDED_MIN_DP -> MEDIUM
            else -> EXPANDED
        }
    }
}

enum class WindowHeightClass {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        const val MEDIUM_MIN_DP: Float = 480f
        const val EXPANDED_MIN_DP: Float = 900f

        fun fromHeightDp(heightDp: Float): WindowHeightClass = when {
            heightDp < MEDIUM_MIN_DP -> COMPACT
            heightDp < EXPANDED_MIN_DP -> MEDIUM
            else -> EXPANDED
        }
    }
}

/** The combination of both classes for a given window. */
data class WindowClass(val width: WindowWidthClass, val height: WindowHeightClass) {
    companion object {
        /**
         * A window lower than 480 dp is never "expanded" in width: a phone turned in landscape
         * (a Galaxy A34 is about 411 x 892 dp) is wider than 840 dp but has no room for the
         * desktop layout (its 64 dp bar, its 280 dp side bar, two panes); it gets the rail.
         */
        fun of(widthDp: Float, heightDp: Float): WindowClass {
            val height = WindowHeightClass.fromHeightDp(heightDp)
            val width = WindowWidthClass.fromWidthDp(widthDp)
            val capped = if (height == WindowHeightClass.COMPACT && width == WindowWidthClass.EXPANDED) WindowWidthClass.MEDIUM else width
            return WindowClass(capped, height)
        }
    }
}
