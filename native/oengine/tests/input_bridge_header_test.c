#include "oengine_input_bridge.h"

#include <stddef.h>

_Static_assert(OENGINE_INPUT_TOUCH == 0 && OENGINE_INPUT_KEYBOARD == 1 &&
               OENGINE_INPUT_MOUSE == 2 && OENGINE_INPUT_GAMEPAD == 3,
               "Android input device ordinals changed");
_Static_assert(OENGINE_INPUT_DOWN == 0 && OENGINE_INPUT_UP == 1 &&
               OENGINE_INPUT_REPEAT == 2 && OENGINE_INPUT_MOVE == 3 &&
               OENGINE_INPUT_SCROLL == 4 && OENGINE_INPUT_TEXT == 5,
               "Android input action ordinals changed");

_Static_assert(sizeof(OEngineNativeInputCallbacks) == 7 * sizeof(void *),
               "callback table must contain seven native function pointers");
_Static_assert(offsetof(OEngineNativeInputEvent, device) == 0,
               "device offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, action) == 4,
               "action offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, code) == 8,
               "code offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, x) == 12,
               "x offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, y) == 16,
               "y offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, value) == 20,
               "value offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, timestamp_nanos) == 24,
               "timestamp offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, modifiers) == 32,
               "modifiers offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, codepoint) == 36,
               "codepoint offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, scan_code) == 40,
               "scancode offset changed");
_Static_assert(offsetof(OEngineNativeInputEvent, reserved) == 44,
               "reserved offset changed");
_Static_assert(sizeof(OEngineNativeInputEvent) == 48,
               "input event stride must remain 48 bytes on every ABI");

static void key_callback(void *window, int32_t key, int32_t scancode,
                         int32_t action, int32_t modifiers) {
    (void)window;
    (void)key;
    (void)scancode;
    (void)action;
    (void)modifiers;
}

static void char_mods_callback(void *window, uint32_t codepoint, int32_t modifiers) {
    (void)window;
    (void)codepoint;
    (void)modifiers;
}

static void platform_callback(void *window, int32_t device, int32_t action,
                              int32_t code, int32_t scan_code, float x, float y,
                              float value, int64_t timestamp_nanos,
                              int32_t modifiers, uint32_t codepoint) {
    (void)window;
    (void)device;
    (void)action;
    (void)code;
    (void)scan_code;
    (void)x;
    (void)y;
    (void)value;
    (void)timestamp_nanos;
    (void)modifiers;
    (void)codepoint;
}

int main(void) {
    OEngineNativeInputCallbacks callbacks = {0};
    OEngineNativeInputEvent event = {0};
    callbacks.key = key_callback;
    callbacks.character_mods = char_mods_callback;
    callbacks.platform_event = platform_callback;
    event.device = 1;
    event.action = OENGINE_INPUT_DOWN;
    event.scan_code = 30;
    return callbacks.key == NULL || callbacks.character_mods == NULL ||
           callbacks.platform_event == NULL || event.device != 1;
}
