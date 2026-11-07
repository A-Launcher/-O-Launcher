package com.alauncher.olauncher

/**
 * Maps Android physical key codes to the GLFW 3 key ABI used by LWJGL.
 *
 * Values follow GLFW 3.4 key definitions. Unknown Android keys return null rather than
 * leaking Android key-code numbers into GLFW's unrelated key namespace.
 */
object OEngineGlfwKeyMapper {
    const val MOD_SHIFT = 0x1
    const val MOD_CONTROL = 0x2
    const val MOD_ALT = 0x4
    const val MOD_SUPER = 0x8
    const val MOD_CAPS_LOCK = 0x10
    const val MOD_NUM_LOCK = 0x20

    fun key(androidKeyCode: Int): Int? {
        // Android A..Z (29..54) and 0..9 (7..16) are contiguous.
        if (androidKeyCode in 29..54) return 65 + androidKeyCode - 29
        if (androidKeyCode in 7..16) return 48 + androidKeyCode - 7
        if (androidKeyCode in 131..142) return 290 + androidKeyCode - 131
        if (androidKeyCode in 144..153) return 320 + androidKeyCode - 144

        return when (androidKeyCode) {
            62 -> 32       // SPACE
            75 -> 39       // APOSTROPHE
            55 -> 44       // COMMA
            69 -> 45       // MINUS
            56 -> 46       // PERIOD
            76 -> 47       // SLASH
            74 -> 59       // SEMICOLON
            70 -> 61       // EQUAL
            71 -> 91       // LEFT_BRACKET
            73 -> 92       // BACKSLASH
            72 -> 93       // RIGHT_BRACKET
            68 -> 96       // GRAVE_ACCENT
            111 -> 256     // ESCAPE
            66 -> 257      // ENTER
            61 -> 258      // TAB
            67 -> 259      // BACKSPACE
            124 -> 260     // INSERT
            112 -> 261     // FORWARD_DELETE
            22 -> 262      // RIGHT
            21 -> 263      // LEFT
            20 -> 264      // DOWN
            19 -> 265      // UP
            92 -> 266      // PAGE_UP
            93 -> 267      // PAGE_DOWN
            122 -> 268     // HOME
            123 -> 269     // END
            115 -> 280     // CAPS_LOCK
            116 -> 281     // SCROLL_LOCK
            143 -> 282     // NUM_LOCK
            120 -> 283     // PRINT_SCREEN
            121 -> 284     // PAUSE
            59 -> 340      // LEFT_SHIFT
            113 -> 341     // LEFT_CONTROL
            57 -> 342      // LEFT_ALT
            117 -> 343     // LEFT_SUPER
            60 -> 344      // RIGHT_SHIFT
            114 -> 345     // RIGHT_CONTROL
            58 -> 346      // RIGHT_ALT
            118 -> 347     // RIGHT_SUPER
            82 -> 348      // MENU
            160 -> 335     // NUMPAD_ENTER
            154 -> 330     // NUMPAD_DOT
            155 -> 331     // NUMPAD_DIVIDE
            156 -> 332     // NUMPAD_MULTIPLY
            157 -> 333     // NUMPAD_SUBTRACT
            158 -> 334     // NUMPAD_ADD
            else -> null
        }
    }

    fun modifiers(metaState: Int): Int {
        var result = 0
        if (metaState and 0x1 != 0 || metaState and 0x40 != 0) result = result or MOD_SHIFT
        if (metaState and 0x1000 != 0 || metaState and 0x2000 != 0) result = result or MOD_CONTROL
        if (metaState and 0x2 != 0 || metaState and 0x10 != 0) result = result or MOD_ALT
        if (metaState and 0x10000 != 0 || metaState and 0x20000 != 0) result = result or MOD_SUPER
        if (metaState and 0x100000 != 0) result = result or MOD_CAPS_LOCK
        if (metaState and 0x200000 != 0) result = result or MOD_NUM_LOCK
        return result
    }

    /** Android mouse button bit flags to GLFW's zero-based button indices. */
    fun mouseButton(androidButton: Int): Int? = when (androidButton) {
        1 -> 0 // BUTTON_PRIMARY
        2 -> 1 // BUTTON_SECONDARY
        4 -> 2 // BUTTON_TERTIARY
        8 -> 3 // BUTTON_BACK
        16 -> 4 // BUTTON_FORWARD
        else -> null
    }
}
