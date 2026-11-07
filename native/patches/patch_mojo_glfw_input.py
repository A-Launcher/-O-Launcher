#!/usr/bin/env python3
"""Apply O Engine's bounded input-queue adapter to the pinned Mojo Android GLFW source."""
from pathlib import Path

source = Path("native/upstream/mojo-glfw/src/android_window.c")
text = source.read_text(encoding="utf-8")

include = '#include "internal.h"\n'
replacement = include + '#include "oengine_input_bridge.h"\n#include <dlfcn.h>\n#include <stdatomic.h>\n'
if "oengine_input_bridge.h" not in text:
    if include not in text:
        raise SystemExit("Pinned Mojo GLFW android_window.c include anchor changed")
    text = text.replace(include, replacement, 1)

owner_anchor = "static _GLFWwindow *surfaceOwner = NULL;\n"
if "static _Atomic int oengine_glfw_ready" not in text:
    if owner_anchor not in text:
        raise SystemExit("Pinned Mojo GLFW surface-owner anchor changed")
    text = text.replace(owner_anchor, owner_anchor + "static _Atomic int oengine_glfw_ready = 0;\n" + "static int32_t oengine_primary_touch_pointer = -1;\n", 1)

# Publish readiness only after GLFW owns a real game window; use atomics because
# the wake function is called from the independent Android input Binder thread.
owner_assignment = "        _glfw.android.focusedWindow = surfaceOwner = window;\n"
if "atomic_store(&oengine_glfw_ready, 1);" not in text:
    if owner_assignment not in text:
        raise SystemExit("Pinned Mojo GLFW window-owner assignment changed")
    text = text.replace(owner_assignment, owner_assignment + "        atomic_store(&oengine_glfw_ready, 1);\n", 1)

destroy_anchor = "    if (_glfw.android.focusedWindow == window)\n        _glfw.android.focusedWindow = NULL;\n"
if "atomic_store(&oengine_glfw_ready, 0);" not in text:
    if destroy_anchor not in text:
        raise SystemExit("Pinned Mojo GLFW window-destroy anchor changed")
    text = text.replace(destroy_anchor, destroy_anchor + "    if (surfaceOwner == window) {\n        surfaceOwner = NULL;\n        oengine_primary_touch_pointer = -1;\n        atomic_store(&oengine_glfw_ready, 0);\n    }\n", 1)

anchor = "void _glfwPollEventsAndroid(void)\n{\n"
bridge = r'''
/*
 * O Engine input is drained only from GLFW's own event-poll thread.
 * The Rust queue is reached by symbol lookup so libglfw remains a separate
 * shared object and does not acquire a link-time dependency on O Engine.
 */
extern EGLSurface _glfwManageEglSurfaceAndroid(_GLFWwindow* window);

typedef size_t (*OEngineInputDispatchFn)(
    void*, const OEngineNativeInputCallbacks*, size_t
);

static void oengine_glfw_key(void* window, int32_t key, int32_t scan,
                             int32_t action, int32_t mods) {
    _glfwInputKey((_GLFWwindow*) window, key, scan, action, mods);
}

static void oengine_glfw_character_mods(void* window, uint32_t codepoint,
                                        int32_t mods) {
    _glfwInputChar((_GLFWwindow*) window, codepoint, mods, GLFW_TRUE);
}

static void oengine_glfw_cursor(void* window, double x, double y) {
    _glfwInputCursorPos((_GLFWwindow*) window, x, y);
}

static void oengine_glfw_mouse(void* window, int32_t button,
                               int32_t action, int32_t mods) {
    _glfwInputMouseClick((_GLFWwindow*) window, button, action, mods);
}

static void oengine_glfw_scroll(void* window, double x, double y) {
    _glfwInputScroll((_GLFWwindow*) window, x, y);
}

/*
 * Android pointer IDs are not guaranteed to start at zero. Track the first
 * active pointer as the desktop primary mouse, and ignore secondary fingers
 * so a second finger cannot release the first finger's held mouse button.
 */
static void oengine_glfw_platform(void* window, int32_t device, int32_t action,
                                  int32_t code, int32_t scan, float x, float y,
                                  float value, int64_t timestamp, int32_t mods,
                                  uint32_t codepoint) {
    (void) scan; (void) value; (void) timestamp; (void) codepoint;
    if (device != 0) return;
    if (action == 0 && oengine_primary_touch_pointer < 0)
        oengine_primary_touch_pointer = code;
    if (code != oengine_primary_touch_pointer) return;

    _GLFWwindow* target = (_GLFWwindow*) window;
    if (action == 0 || action == 1 || action == 3)
        _glfwInputCursorPos(target, x, y);
    if (action == 0)
        _glfwInputMouseClick(target, GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS, mods);
    else if (action == 1) {
        _glfwInputMouseClick(target, GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE, mods);
        oengine_primary_touch_pointer = -1;
    }
}

/* Wake GLFW's native wait-event condition after Rust accepts a new batch. */
void oengine_wake_input(void) {
    if (atomic_load(&oengine_glfw_ready))
        glfwPostEmptyEvent();
}

static void oengine_drain_input_events(void) {
    static OEngineInputDispatchFn dispatch = NULL;
    static void* native_handle = NULL;
    static uint32_t (*process_surface)(void) = NULL;
    if (!native_handle)
        native_handle = dlopen("liboengine_native.so", RTLD_NOW | RTLD_LOCAL);
    if (native_handle && !process_surface)
        process_surface = (uint32_t (*)(void)) dlsym(native_handle, "oengine_ltw_egl_process_pending_window");
    /* EGL is mutated only here, on the LTW/GLFW owner thread, never on Binder. */
    if (process_surface) process_surface();
    /* Surface destruction can block the ART callback until surfaceInUse clears.
       Apply the tracked pbuffer/window transition here to release that waiter. */
    if (surfaceOwner && (surfaceDestroyed || surfaceUpdated))
        _glfwManageEglSurfaceAndroid(surfaceOwner);
    if (!surfaceOwner) return;
    if (!dispatch) {
        if (native_handle)
            dispatch = (OEngineInputDispatchFn) dlsym(native_handle, "oengine_input_dispatch");
    }
    if (!dispatch) return;

    const OEngineNativeInputCallbacks callbacks = {
        .key = oengine_glfw_key,
        /* GLFW's internal char path fans out to both char callback variants. */
        .character = NULL,
        .character_mods = oengine_glfw_character_mods,
        .cursor_position = oengine_glfw_cursor,
        .mouse_button = oengine_glfw_mouse,
        .scroll = oengine_glfw_scroll,
        .platform_event = oengine_glfw_platform
    };
    dispatch(surfaceOwner, &callbacks, OENGINE_INPUT_MAX_RESET_RELEASES);
}

'''
if "static void oengine_drain_input_events(void)" not in text:
    if anchor not in text:
        raise SystemExit("Pinned Mojo GLFW poll-events anchor changed")
    text = text.replace(anchor, bridge + anchor, 1)

text = text.replace(
    "void _glfwPollEventsAndroid(void)\n{\n    process_flag_bits();",
    "void _glfwPollEventsAndroid(void)\n{\n    oengine_drain_input_events();\n    process_flag_bits();",
    1,
)
text = text.replace(
    "void _glfwWaitEventsAndroid(void)\n{\n    if(update_flags != 0) {",
    "void _glfwWaitEventsAndroid(void)\n{\n    oengine_drain_input_events();\n    if(update_flags != 0) {",
    1,
)
text = text.replace(
    "    _input_queue_wait(&input_queue, android_dequeue_event);\n    process_flag_bits();",
    "    _input_queue_wait(&input_queue, android_dequeue_event);\n    oengine_drain_input_events();\n    process_flag_bits();",
    1,
)
text = text.replace(
    "    process_flag_bits();\n}\n\nvoid _glfwPostEmptyEventAndroid",
    "    oengine_drain_input_events();\n    process_flag_bits();\n}\n\nvoid _glfwPostEmptyEventAndroid",
    1,
)

text = text.replace(
    "void _glfwWaitEventsTimeoutAndroid(double timeout)\n{\n    if(update_flags != 0) {",
    "void _glfwWaitEventsTimeoutAndroid(double timeout)\n{\n    oengine_drain_input_events();\n    if(update_flags != 0) {",
    1,
)

if "_glfwManageEglSurfaceAndroid(surfaceOwner)" not in text:
    raise SystemExit("GLFW event thread does not switch LTW surface modes to release Surface waiters")
if "oengine_ltw_egl_process_pending_window" not in text or "process_surface()" not in text:
    raise SystemExit("GLFW event thread does not apply queued LTW Surface transitions")
if "oengine_wake_input" not in text or "glfwPostEmptyEvent();" not in text:
    raise SystemExit("O Engine GLFW wake bridge was not installed")
if "static int32_t oengine_primary_touch_pointer = -1;" not in text:
    raise SystemExit("Primary-touch pointer tracking was not installed")
if "if (code != oengine_primary_touch_pointer) return;" not in text:
    raise SystemExit("Secondary touch pointers are not isolated from the primary mouse")
if "if (surfaceOwner == window) {" not in text:
    raise SystemExit("GLFW destroy path does not guard the active surface owner")
if "surfaceOwner = NULL;" not in text or "oengine_primary_touch_pointer = -1;" not in text or "atomic_store(&oengine_glfw_ready, 0);" not in text:
    raise SystemExit("GLFW destroy path does not clear owner, touch pointer and readiness together")
if "atomic_store(&oengine_glfw_ready, 1);" not in text:
    raise SystemExit("GLFW create path does not publish readiness")
if text.count("static int32_t oengine_primary_touch_pointer = -1;") != 1:
    raise SystemExit("Primary-touch state must have exactly one definition")
# Keep the callback adapter contract explicit: key scancodes/modifiers and GLFW
# PRESS/RELEASE semantics must survive the Rust-to-Mojo boundary.
for required in (
    "_glfwInputKey((_GLFWwindow*) window, key, scan, action, mods);",
    "_glfwInputChar((_GLFWwindow*) window, codepoint, mods, GLFW_TRUE);",
    "_glfwInputCursorPos((_GLFWwindow*) window, x, y);",
    "_glfwInputMouseClick((_GLFWwindow*) window, button, action, mods);",
    "_glfwInputScroll((_GLFWwindow*) window, x, y);",
    "GLFW_MOUSE_BUTTON_LEFT, GLFW_PRESS",
    "GLFW_MOUSE_BUTTON_LEFT, GLFW_RELEASE",
):
    if required not in text:
        raise SystemExit("GLFW callback adapter contract missing: " + required)

poll_start = text.index("void _glfwPollEventsAndroid(void)")
wait_start = text.index("void _glfwWaitEventsAndroid(void)")
timeout_start = text.index("void _glfwWaitEventsTimeoutAndroid(double timeout)")
post_start = text.index("void _glfwPostEmptyEventAndroid(void)")
if text[poll_start:wait_start].count("oengine_drain_input_events();") < 1:
    raise SystemExit("GLFW poll path does not drain O Engine input")
if text[wait_start:timeout_start].count("oengine_drain_input_events();") < 2:
    raise SystemExit("GLFW wait path must drain before and after its wait")
if text[timeout_start:post_start].count("oengine_drain_input_events();") < 2:
    raise SystemExit("GLFW timed-wait path must drain before and after its wait")

# ANativeWindow_fromSurface already returns an acquired reference. Keep exactly
# one native reference so nativeSurfaceDestroyed's single release is balanced.
surface_create_anchor = '''    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    ANativeWindow_acquire(window);
    LOGI("Acquired native window: %p", window);
'''
surface_create_replacement = '''    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Could not acquire ANativeWindow from Android Surface");
        return;
    }
    pthread_mutex_lock(&nw_egl_mutex);
    nativeWindow = window;
    surfaceDestroyed = false;
    pthread_mutex_unlock(&nw_egl_mutex);
    LOGI("Acquired native window: %p", window);
'''
if "Could not acquire ANativeWindow from Android Surface" not in text:
    if surface_create_anchor not in text:
        raise SystemExit("Pinned Mojo nativeSurfaceCreated ownership anchor changed")
    text = text.replace(surface_create_anchor, surface_create_replacement, 1)

# Surface callbacks must wake a GLFW thread blocked in glfwWaitEvents.
# nativeSurfaceDestroyed waits for surfaceInUse to clear, so the game thread must
# be woken before that wait begins; the patched LTW surface manager then switches
# to the tracked pbuffer and broadcasts nw_egl_cond.
surface_update_anchor = '''Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated(JNIEnv *env, jclass clazz) {
    surfaceUpdated = true;
}'''
surface_update_replacement = '''Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceUpdated(JNIEnv *env, jclass clazz) {
    surfaceUpdated = true;
    if (atomic_load(&oengine_glfw_ready)) {
        input_event_t wake = {0};
        wake.type = GLFW_ANDROID_EVENT_TYPE_EMPTY;
        android_send_event(&wake);
    }
}'''
if "input_event_t wake = {0};" not in text:
    if surface_update_anchor not in text:
        raise SystemExit("Pinned Mojo nativeSurfaceUpdated wake anchor changed")
    text = text.replace(surface_update_anchor, surface_update_replacement, 1)

surface_destroy_anchor = '''JNIEXPORT void JNICALL
Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed(JNIEnv *env,
                                                                           jclass clazz) {
    surfaceDestroyed = true;
    if(!ownedByVulkan && surfaceInUse) {
        pthread_mutex_lock(&nw_egl_mutex);
        pthread_cond_wait(&nw_egl_cond, &nw_egl_mutex);
        LOGI("Unhalted after window destruction");
        ANativeWindow_release(nativeWindow);
        nativeWindow = NULL;
        pthread_mutex_unlock(&nw_egl_mutex);
    }else {
        ANativeWindow_release(nativeWindow);
        nativeWindow = NULL;
    }

}'''
surface_destroy_replacement = '''JNIEXPORT void JNICALL
Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed(JNIEnv *env,
                                                                           jclass clazz) {
    pthread_mutex_lock(&nw_egl_mutex);
    surfaceDestroyed = true;
    pthread_mutex_unlock(&nw_egl_mutex);
    if (atomic_load(&oengine_glfw_ready)) {
        input_event_t wake = {0};
        wake.type = GLFW_ANDROID_EVENT_TYPE_EMPTY;
        android_send_event(&wake);
    }
    if (!ownedByVulkan) {
        pthread_mutex_lock(&nw_egl_mutex);
        /* Test the predicate under the condition mutex to avoid a lost wake
           if the GLFW thread switches to pbuffer before this waiter sleeps. */
        while (surfaceInUse)
            pthread_cond_wait(&nw_egl_cond, &nw_egl_mutex);
        if (nativeWindow) {
            ANativeWindow_release(nativeWindow);
            nativeWindow = NULL;
        }
        pthread_mutex_unlock(&nw_egl_mutex);
    } else if (nativeWindow) {
        ANativeWindow_release(nativeWindow);
        nativeWindow = NULL;
    }
}'''
if "while (surfaceInUse)" not in text:
    if surface_destroy_anchor not in text:
        raise SystemExit("Pinned Mojo nativeSurfaceDestroyed full function anchor changed")
    text = text.replace(surface_destroy_anchor, surface_destroy_replacement, 1)
if "while (surfaceInUse)" not in text or "pthread_cond_wait(&nw_egl_cond, &nw_egl_mutex);" not in text:
    raise SystemExit("Android Surface destroy waiter must use a mutex-protected predicate loop")


# Surface callbacks can arrive before glfwInit and after glfwTerminate. Keep
# the EGL condition primitives statically initialized for the entire library
# lifetime instead of creating/destroying them with the GLFW window subsystem.
mutex_anchor = '''static pthread_mutex_t nw_egl_mutex;
static pthread_cond_t nw_egl_cond;
static pthread_mutex_t nw_vulkan_mutex;
static pthread_cond_t nw_vulkan_cond;'''
mutex_replacement = '''static pthread_mutex_t nw_egl_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t nw_egl_cond = PTHREAD_COND_INITIALIZER;
static pthread_mutex_t nw_vulkan_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t nw_vulkan_cond = PTHREAD_COND_INITIALIZER;'''
if "PTHREAD_MUTEX_INITIALIZER" not in text:
    if mutex_anchor not in text:
        raise SystemExit("Pinned Mojo EGL/Vulkan mutex declarations changed")
    text = text.replace(mutex_anchor, mutex_replacement, 1)

init_anchor = '''GLFWbool android_init_window(void) {
    if(pthread_mutex_init(&nw_egl_mutex, NULL) != 0) return GLFW_FALSE;
    if(pthread_cond_init(&nw_egl_cond, NULL) != 0) goto fail1;
    if(pthread_mutex_init(&nw_vulkan_mutex, NULL) != 0) goto fail2;
    if(pthread_cond_init(&nw_vulkan_cond, NULL) != 0) goto fail3;
    if(!_input_queue_init(&input_queue)) goto fail4;
    return GLFW_TRUE;

    fail4:
    pthread_cond_destroy(&nw_vulkan_cond);
    fail3:
    pthread_mutex_destroy(&nw_vulkan_mutex);
    fail2:
    pthread_cond_destroy(&nw_egl_cond);
    fail1:
    pthread_mutex_destroy(&nw_egl_mutex);
    return GLFW_FALSE;
}

void android_destroy_window(void) {
    pthread_mutex_destroy(&nw_egl_mutex);
    pthread_cond_destroy(&nw_egl_cond);
    pthread_mutex_destroy(&nw_vulkan_mutex);
    pthread_cond_destroy(&nw_vulkan_cond);
    _input_queue_destroy(&input_queue);
}'''
init_replacement = '''GLFWbool android_init_window(void) {
    return _input_queue_init(&input_queue) ? GLFW_TRUE : GLFW_FALSE;
}

void android_destroy_window(void) {
    pthread_mutex_lock(&nw_egl_mutex);
    surfaceInUse = false;
    pthread_cond_broadcast(&nw_egl_cond);
    pthread_mutex_unlock(&nw_egl_mutex);
    _input_queue_destroy(&input_queue);
}'''
if "pthread_mutex_init(&nw_egl_mutex" in text:
    if init_anchor not in text:
        raise SystemExit("Pinned Mojo Android window mutex lifecycle changed")
    text = text.replace(init_anchor, init_replacement, 1)
if "pthread_mutex_init(&nw_egl_mutex" in text or "pthread_mutex_destroy(&nw_egl_mutex" in text:
    raise SystemExit("EGL condition mutexes must remain valid across Surface callbacks")
if "PTHREAD_MUTEX_INITIALIZER" not in text or "PTHREAD_COND_INITIALIZER" not in text:
    raise SystemExit("Android Surface condition primitives are not statically initialized")

source.write_text(text, encoding="utf-8")
print("Patched pinned Mojo Android GLFW poll/wait paths and lifetime-safe Surface condition primitives.")
