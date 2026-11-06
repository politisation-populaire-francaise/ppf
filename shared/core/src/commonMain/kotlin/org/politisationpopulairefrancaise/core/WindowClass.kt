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
        fun of(widthDp: Float, heightDp: Float): WindowClass =
            WindowClass(WindowWidthClass.fromWidthDp(widthDp), WindowHeightClass.fromHeightDp(heightDp))
    }
}
