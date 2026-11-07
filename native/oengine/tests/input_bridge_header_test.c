#include "oengine_input_bridge.h"

#include <stddef.h>

_Static_assert(sizeof(OEngineNativeInputCallbacks) == 7 * sizeof(void *),
               "callback table must contain six native function pointers");
_Static_assert(offsetof(OEngineNativeInputEvent, timestamp_nanos) >=
                   3 * sizeof(int32_t) + 3 * sizeof(float),
               "timestamp must follow the three float payload fields");

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
