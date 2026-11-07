#ifndef OENGINE_INPUT_BRIDGE_H
#define OENGINE_INPUT_BRIDGE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#define OENGINE_INPUT_MAX_RESET_RELEASES 1024u

/* Keep field order and integer values aligned with OEngineInputDevice/Action.
 * DOWN=0, UP=1, REPEAT=2, MOVE=3, SCROLL=4 are O Engine values;
 * oengine_input_dispatch converts digital actions to GLFW PRESS=1, RELEASE=0, REPEAT=2.
 */
typedef struct OEngineNativeInputEvent {
    int32_t device;          /* TOUCH=0, KEYBOARD=1, MOUSE=2, GAMEPAD=3 */
    int32_t action;          /* DOWN=0, UP=1, REPEAT=2, MOVE=3, SCROLL=4 */
    int32_t code;
    float x;
    float y;
    float value;
    int64_t timestamp_nanos;
    int32_t modifiers;
    uint32_t codepoint;
    int32_t scan_code;       /* Android physical scan code; passed to GLFW key callback. */
} OEngineNativeInputEvent;

/* Drain on the GLFW/game thread. Supply room for up to 1024 reset releases. */
size_t oengine_input_drain(OEngineNativeInputEvent *output, size_t capacity);

/*
 * Optional callback dispatcher for a native GLFW backend. Call only from the game's
 * event-poll thread. Callback pointers must be the active HotSpot GLFW callbacks.
 * Use capacity >= OENGINE_INPUT_MAX_RESET_RELEASES to guarantee overflow releases fit.
 * Touch/gamepad events are preserved through platform_event for backend-specific handling.
 */
typedef void (*OEngineGlfwKeyCallback)(void *window, int32_t key, int32_t scancode, int32_t action, int32_t mods);
typedef void (*OEngineGlfwCharCallback)(void *window, uint32_t codepoint);
typedef void (*OEngineGlfwCursorPosCallback)(void *window, double x, double y);
typedef void (*OEngineGlfwMouseButtonCallback)(void *window, int32_t button, int32_t action, int32_t mods);
typedef void (*OEngineGlfwScrollCallback)(void *window, double x, double y);
typedef void (*OEnginePlatformInputCallback)(
    void *window, int32_t device, int32_t action, int32_t code,
    float x, float y, float value, int64_t timestamp_nanos,
    int32_t modifiers, uint32_t codepoint
);

typedef struct OEngineNativeInputCallbacks {
    OEngineGlfwKeyCallback key;
    OEngineGlfwCharCallback character;
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
