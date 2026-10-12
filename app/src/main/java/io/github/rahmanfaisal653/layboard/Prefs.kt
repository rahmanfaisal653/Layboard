package io.github.rahmanfaisal653.layboard

import android.content.Context

/** The only things Layboard remembers: the last Host and the pointer speed. */
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
        get() = 0.5f + 0.25f * speedStep

    companion object {
        const val MAX_SPEED_STEP = 10
        private const val DEFAULT_SPEED_STEP = 4
        private const val KEY_LAST_HOST = "last_host"
        private const val KEY_SPEED_STEP = "speed_step"
    }
}
