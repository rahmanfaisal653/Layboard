package io.github.rahmanfaisal653.layboard

import android.content.Context

/** The only things Layboard remembers: the last Host and the Pointer and Scroll Speed. */
class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("layboard", Context.MODE_PRIVATE)

    var lastHostAddress: String?
        get() = prefs.getString(KEY_LAST_HOST, null)
        set(value) = prefs.edit().putString(KEY_LAST_HOST, value).apply()

    /** Slider position, 0..[MAX_SPEED_STEP]. */
    var speedStep: Int
        get() = prefs.getInt(KEY_SPEED_STEP, DEFAULT_SPEED_STEP)
        set(value) = prefs.edit().putInt(KEY_SPEED_STEP, value).apply()

    val speedMultiplier: Float
        get() = multiplierFor(speedStep)

    /** Scroll Speed slider position, 0..[MAX_SPEED_STEP]. */
    var scrollSpeedStep: Int
        get() = prefs.getInt(KEY_SCROLL_SPEED_STEP, DEFAULT_SCROLL_SPEED_STEP)
        set(value) = prefs.edit().putInt(KEY_SCROLL_SPEED_STEP, value).apply()

    val scrollSpeedMultiplier: Float
        get() = multiplierFor(scrollSpeedStep)

    companion object {
        const val MAX_SPEED_STEP = 10

        /** The speed multiplier a slider position stands for, 0.5 at its start and 3.0 at its end. */
        fun multiplierFor(step: Int): Float = 0.5f + 0.25f * step.coerceIn(0, MAX_SPEED_STEP)

        private const val DEFAULT_SPEED_STEP = 4

        /** A multiplier of 1.0, the scroll scale Layboard had before Scroll Speed existed. */
        private const val DEFAULT_SCROLL_SPEED_STEP = 2
        private const val KEY_LAST_HOST = "last_host"
        private const val KEY_SPEED_STEP = "speed_step"
        private const val KEY_SCROLL_SPEED_STEP = "scroll_speed_step"
    }
}
