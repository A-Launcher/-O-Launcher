package com.alauncher.olauncher

/**
 * Normalizes Android KeyEvent keycodes to the pinned Mojo GLFW 14-button layout.
 * Slot order: A, B, X, Y, L1, R1, SELECT, START, L3, R3, D-pad U/R/D/L.
 */
object OEngineAndroidGamepadMapper {
    fun buttonIndex(androidKeyCode: Int): Int? = when (androidKeyCode) {
        96 -> 0  // KEYCODE_BUTTON_A
        97 -> 1  // KEYCODE_BUTTON_B
        99 -> 2  // KEYCODE_BUTTON_X
        100 -> 3 // KEYCODE_BUTTON_Y
        102 -> 4 // KEYCODE_BUTTON_L1
        103 -> 5 // KEYCODE_BUTTON_R1
        109 -> 6 // KEYCODE_BUTTON_SELECT / GLFW back
        108 -> 7 // KEYCODE_BUTTON_START
        106 -> 8 // KEYCODE_BUTTON_THUMBL
        107 -> 9 // KEYCODE_BUTTON_THUMBR
        19 -> 10 // DPAD_UP
        22 -> 11 // DPAD_RIGHT
        20 -> 12 // DPAD_DOWN
        21 -> 13 // DPAD_LEFT
        else -> null // GUIDE and unknown keys have no slot in this Mojo ABI.
    }
}
