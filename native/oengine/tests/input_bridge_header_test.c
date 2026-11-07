#include "oengine_input_bridge.h"

#include <stddef.h>

_Static_assert(sizeof(OEngineNativeInputCallbacks) == 7 * sizeof(void *),
               "callback table must contain six native function pointers");
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

int main(void) {
    OEngineNativeInputCallbacks callbacks = {0};
    OEngineNativeInputEvent event = {0};
    callbacks.key = key_callback;
    event.device = 1;
    event.action = 0;
    event.scan_code = 30;
    return callbacks.key == NULL || event.device != 1;
}
