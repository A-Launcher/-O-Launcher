#ifndef OENGINE_LTW_EGL_H
#define OENGINE_LTW_EGL_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Borrowed view of an O Engine LTW-tracked EGL session.
 *
 * The session owns every handle and function pointer. The caller must not retain
 * these beyond the session lifetime, destroy the shared context, or invoke the
 * functions from a thread other than the session's creating game thread.
 *
 * This ABI exists so an Android GLFW adapter can create an LTW-tracked shared
 * context instead of bypassing LTW with a second untracked EGL context.
 */
typedef struct OEngineLtwEglShareInfo {
    void* display;
    void* config;
    void* context;
    void* pbuffer;
    void* window_surface;
    void* create_context;
    void* make_current;
    void* destroy_context;
    void* destroy_surface;
    void* swap_buffers;
} OEngineLtwEglShareInfo;

/* Returns 1 on success, 0 on failure. Output must point to a writable struct. */
uint32_t oengine_ltw_egl_share_info(
    uint64_t session_handle,
    OEngineLtwEglShareInfo* output
);

#ifdef __cplusplus
}
#endif

#endif
