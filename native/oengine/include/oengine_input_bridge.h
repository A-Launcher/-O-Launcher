#ifndef OENGINE_INPUT_BRIDGE_H
#define OENGINE_INPUT_BRIDGE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Keep field order and integer values aligned with OEngineInputDevice/Action. */
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
} OEngineNativeInputEvent;

/* Drain on the GLFW/game thread. Supply room for up to 1024 reset releases. */
size_t oengine_input_drain(OEngineNativeInputEvent *output, size_t capacity);
bool oengine_input_reset_pending(void);
void oengine_input_clear_session(void);

#endif
