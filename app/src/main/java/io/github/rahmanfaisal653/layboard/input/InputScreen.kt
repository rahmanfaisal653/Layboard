package io.github.rahmanfaisal653.layboard.input

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import io.github.rahmanfaisal653.layboard.R
import io.github.rahmanfaisal653.layboard.hid.HidController
import io.github.rahmanfaisal653.layboard.hid.HidReports
import io.github.rahmanfaisal653.layboard.hid.HidTrace
import io.github.rahmanfaisal653.layboard.hid.Key
import io.github.rahmanfaisal653.layboard.hid.KeyMap
import io.github.rahmanfaisal653.layboard.keyboard.KeyboardCaptureView
import io.github.rahmanfaisal653.layboard.keyboard.TypedText
import io.github.rahmanfaisal653.layboard.mouse.MouseAreaView

/** The Keyboard, Mouse and Click Buttons, shown while a Connection is active. */
class InputScreen(
    context: Context,
    private val hid: HidController,
    onDisconnect: () -> Unit,
) {
    val view: View = LayoutInflater.from(context).inflate(R.layout.screen_input, null)

    private val inputMethods = context.getSystemService(InputMethodManager::class.java)
    private val hostName: TextView = view.findViewById(R.id.input_host_name)
    private val mouse: MouseAreaView = view.findViewById(R.id.input_mouse)
    private val capture: KeyboardCaptureView = view.findViewById(R.id.input_capture)

    // The keyboard only attaches to a view in the focused window, which a fresh screen may not be yet.
    private var waitingForFocus = false
    private val showOnFocus = object : ViewTreeObserver.OnWindowFocusChangeListener {
        override fun onWindowFocusChanged(hasFocus: Boolean) {
            if (!hasFocus) return
            waitingForFocus = false
            capture.viewTreeObserver.removeOnWindowFocusChangeListener(this)
            if (capture.isAttachedToWindow) showKeyboard()
        }
    }

    private val specialKeys = mapOf(
        R.id.key_esc to Key(KeyMap.ESCAPE),
        R.id.key_tab to Key(KeyMap.TAB),
        R.id.key_ctrl to Key(0, HidReports.MOD_CTRL),
        R.id.key_alt to Key(0, HidReports.MOD_ALT),
        R.id.key_win to Key(0, HidReports.MOD_GUI),
        R.id.key_left to Key(KeyMap.LEFT),
        R.id.key_up to Key(KeyMap.UP),
        R.id.key_down to Key(KeyMap.DOWN),
        R.id.key_right to Key(KeyMap.RIGHT),
    )

    private val clickButtons = mapOf(
        R.id.click_left to HidReports.BUTTON_LEFT,
        R.id.click_right to HidReports.BUTTON_RIGHT,
    )

    init {
        // A correction goes out like any other key, with the held Special Keys, as a real keyboard's would.
        val send = { key: Key -> hid.sendKey(key.modifiers, key.usage) }
        capture.typing = TypedText(send, send, hid::note)
        mouse.onMove = { dx, dy -> hid.moveMouse(dx, dy) }
        mouse.onScroll = { wheel, pan -> hid.scrollMouse(wheel, pan) }

        view.findViewById<View>(R.id.input_keyboard).setOnClickListener { showKeyboard() }
        view.findViewById<View>(R.id.input_disconnect).setOnClickListener {
            hideKeyboard()
            onDisconnect()
        }

        clickButtons.forEach { (id, mask) -> bindHeld(id, { hid.setClickButton(mask, true) }, { hid.setClickButton(mask, false) }) }
        specialKeys.forEach { (id, key) -> bindHeld(id, { hid.holdKey(key) }, { hid.releaseKey(key) }) }
    }

    fun bind(host: String, pointerSpeed: Float) {
        hostName.text = host
        mouse.speed = pointerSpeed
    }

    /** Opens the Phone's keyboard on the capture target, waiting for window focus if it has none yet. */
    fun showKeyboard() {
        capture.requestFocus()
        if (!capture.hasWindowFocus()) {
            if (!waitingForFocus) {
                waitingForFocus = true
                capture.viewTreeObserver.addOnWindowFocusChangeListener(showOnFocus)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Waits for the keyboard to attach to the newly focused view instead of failing when it has not yet.
            capture.windowInsetsController?.show(WindowInsets.Type.ime())
            hid.note("keyboard open requested")
            return
        }
        val shown = inputMethods.showSoftInput(capture, 0)
        hid.note("keyboard open ${HidTrace.verdict(shown)}")
        // Right after a screen swap the keyboard may not serve the capture target yet; try once more.
        if (!shown) capture.post { inputMethods.showSoftInput(capture, 0) }
    }

    private fun hideKeyboard() {
        view.windowToken?.let { inputMethods.hideSoftInputFromWindow(it, 0) }
    }

    /**
     * Another screen replaces this one, as when the Connection ends: closes the keyboard and lets go of
     * the Click Buttons and Special Keys, so the next Connection starts with nothing held. The Host side
     * is released by the Connection ending, before its link closes.
     */
    fun leave() {
        hideKeyboard()
        (clickButtons.keys + specialKeys.keys).forEach { view.findViewById<View>(it).isPressed = false }
    }

    /** Makes button [id] act like a real key or button: [down] on touch down, [up] on touch up or cancel. */
    @SuppressLint("ClickableViewAccessibility")
    private fun bindHeld(id: Int, down: () -> Unit, up: () -> Unit) {
        view.findViewById<View>(id).setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    button.isPressed = true
                    down()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    button.isPressed = false
                    up()
                }
            }
            true
        }
    }
}
