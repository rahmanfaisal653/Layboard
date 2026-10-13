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
import io.github.rahmanfaisal653.layboard.hid.KeyMap
import io.github.rahmanfaisal653.layboard.keyboard.KeyboardCaptureView
import io.github.rahmanfaisal653.layboard.keyboard.KeyboardSender
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
    private val sender = KeyboardSender(hid::sendKey, hid::setClickButton)

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

    private val modifierButtons = mapOf(
        R.id.key_ctrl to HidReports.MOD_CTRL,
        R.id.key_alt to HidReports.MOD_ALT,
        R.id.key_win to HidReports.MOD_GUI,
    )

    init {
        capture.typing = TypedText(sender::press, sender::correct, hid::note)
        mouse.onMove = { dx, dy -> hid.moveMouse(dx, dy) }

        view.findViewById<View>(R.id.input_keyboard).setOnClickListener { showKeyboard() }
        view.findViewById<View>(R.id.input_disconnect).setOnClickListener {
            hideKeyboard()
            onDisconnect()
        }

        bindClickButton(R.id.click_left, HidReports.BUTTON_LEFT)
        bindClickButton(R.id.click_right, HidReports.BUTTON_RIGHT)

        mapOf(
            R.id.key_esc to KeyMap.ESCAPE,
            R.id.key_tab to KeyMap.TAB,
            R.id.key_left to KeyMap.LEFT,
            R.id.key_up to KeyMap.UP,
            R.id.key_down to KeyMap.DOWN,
            R.id.key_right to KeyMap.RIGHT,
        ).forEach { (id, usage) ->
            view.findViewById<View>(id).setOnClickListener { sender.press(usage) }
        }

        modifierButtons.forEach { (id, modifier) ->
            view.findViewById<View>(id).setOnClickListener { sender.toggleModifier(modifier) }
        }
        // A long press on Win sends it alone, e.g. to open the Start menu.
        view.findViewById<View>(R.id.key_win).setOnLongClickListener {
            sender.tapWin()
            true
        }
        sender.onArmedChanged = { armed ->
            modifierButtons.forEach { (id, modifier) ->
                view.findViewById<View>(id).isActivated = armed and modifier != 0
            }
        }
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

    /** Another screen replaces this one, as when the Connection ends: closes the keyboard and clears any Armed modifier. */
    fun leave() {
        hideKeyboard()
        sender.disarm()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bindClickButton(id: Int, mask: Int) {
        view.findViewById<View>(id).setOnTouchListener { button, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    button.isPressed = true
                    sender.click(mask, true)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    button.isPressed = false
                    sender.click(mask, false)
                }
            }
            true
        }
    }
}
