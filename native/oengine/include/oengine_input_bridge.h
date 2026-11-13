#ifndef OENGINE_INPUT_BRIDGE_H
#define OENGINE_INPUT_BRIDGE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define OENGINE_INPUT_MAX_RESET_RELEASES 1031u

/* Keep these values aligned with OEngineInputDevice/OEngineInputAction ordinals.
 * TEXT carries one committed Unicode scalar per event; it is never a key transition.
 * oengine_input_dispatch converts digital actions to GLFW PRESS=1, RELEASE=0, REPEAT=2.
 */
enum OEngineInputDevice {
    OENGINE_INPUT_TOUCH = 0,
    OENGINE_INPUT_KEYBOARD = 1,
    OENGINE_INPUT_MOUSE = 2,
    OENGINE_INPUT_GAMEPAD = 3
};

enum OEngineInputAction {
    OENGINE_INPUT_DOWN = 0,
    OENGINE_INPUT_UP = 1,
    OENGINE_INPUT_REPEAT = 2,
    OENGINE_INPUT_MOVE = 3,
    OENGINE_INPUT_SCROLL = 4,
    OENGINE_INPUT_TEXT = 5
};
typedef struct OEngineNativeInputEvent {
    int32_t device;          /* TOUCH=0, KEYBOARD=1, MOUSE=2, GAMEPAD=3 */
    int32_t action;          /* OEngineInputAction; TEXT=5 carries codepoint. */
    int32_t code;
    float x;
    float y;
    float value;
    int64_t timestamp_nanos;
    int32_t modifiers;
    uint32_t codepoint;
    int32_t scan_code;       /* Android physical scan code; passed to GLFW key callback. */
    int32_t reserved;        /* Must be zero; keeps the event stride 48 bytes on every ABI. */
} OEngineNativeInputEvent;

/* Drain on the GLFW/game thread. Supply room for up to 1024 digital releases plus 7 gamepad-axis resets. */
size_t oengine_input_drain(OEngineNativeInputEvent *output, size_t capacity);

/*
 * Optional callback dispatcher for a native GLFW backend. Call only from the game's
 * event-poll thread. Callback pointers must be the active HotSpot GLFW callbacks.
 * Use capacity >= OENGINE_INPUT_MAX_RESET_RELEASES to guarantee overflow releases fit.
 * Touch/gamepad events are preserved through platform_event for backend-specific handling.
 */
typedef void (*OEngineGlfwKeyCallback)(void *window, int32_t key, int32_t scancode, int32_t action, int32_t mods);
typedef void (*OEngineGlfwCharCallback)(void *window, uint32_t codepoint);
typedef void (*OEngineGlfwCharModsCallback)(void *window, uint32_t codepoint, int32_t mods);
typedef void (*OEngineGlfwCursorPosCallback)(void *window, double x, double y);
typedef void (*OEngineGlfwMouseButtonCallback)(void *window, int32_t button, int32_t action, int32_t mods);
typedef void (*OEngineGlfwScrollCallback)(void *window, double x, double y);
typedef void (*OEnginePlatformInputCallback)(
    void *window, int32_t device, int32_t action, int32_t code, int32_t scan_code,
    float x, float y, float value, int64_t timestamp_nanos,
    int32_t modifiers, uint32_t codepoint
);

typedef struct OEngineNativeInputCallbacks {
    OEngineGlfwKeyCallback key;
    OEngineGlfwCharCallback character;
    OEngineGlfwCharModsCallback character_mods;
    OEngineGlfwCursorPosCallback cursor_position;
    OEngineGlfwMouseButtonCallback mouse_button;
    OEngineGlfwScrollCallback scroll;
    OEnginePlatformInputCallback platform_event;
} OEngineNativeInputCallbacks;

size_t oengine_input_dispatch(
    void *window, const OEngineNativeInputCallbacks *callbacks, size_t capacity
);
bool oengine_input_reset_pending(void);
void oengine_input_clear_session(void);

#endif
