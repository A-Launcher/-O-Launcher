#!/usr/bin/env python3
"""Make pinned Mojo Android GLFW borrow O Engine's LTW-tracked EGL context."""
from pathlib import Path

source = Path("native/upstream/mojo-glfw/src/egl_context.c")
text = source.read_text(encoding="utf-8")

include_anchor = '#include "internal.h"\n'
include_replacement = include_anchor + r'''
#if defined(_GLFW_ANDROID)
#include "oengine_ltw_egl.h"
#include <dlfcn.h>

/* The LTW session is created on the HotSpot owner thread before GLFW starts. */
typedef uint32_t (*OEngineActiveShareInfoFn)(OEngineLtwEglShareInfo*);
typedef uint32_t (*OEngineProcessPendingWindowFn)(void);
static void* oengine_ltw_handle = NULL;
static OEngineActiveShareInfoFn oengine_ltw_share_info_fn = NULL;
static OEngineProcessPendingWindowFn oengine_ltw_process_pending_fn = NULL;

static GLFWbool oengine_get_ltw_share_info(OEngineLtwEglShareInfo* info)
{
    if (!oengine_ltw_handle)
        oengine_ltw_handle = dlopen("liboengine_native.so", RTLD_NOW | RTLD_LOCAL);
    if (!oengine_ltw_share_info_fn && oengine_ltw_handle)
        oengine_ltw_share_info_fn = (OEngineActiveShareInfoFn)
            dlsym(oengine_ltw_handle, "oengine_ltw_egl_active_share_info");
    return oengine_ltw_share_info_fn && info &&
        oengine_ltw_share_info_fn(info) == 1;
}

/* Surface IPC only queues ownership changes. Drain them on the EGL/GLFW owner thread. */
static GLFWbool oengine_process_pending_ltw_window(void)
{
    if (!oengine_ltw_handle)
        oengine_ltw_handle = dlopen("liboengine_native.so", RTLD_NOW | RTLD_LOCAL);
    if (!oengine_ltw_process_pending_fn && oengine_ltw_handle)
        oengine_ltw_process_pending_fn = (OEngineProcessPendingWindowFn)
            dlsym(oengine_ltw_handle, "oengine_ltw_egl_process_pending_window");
    if (!oengine_ltw_process_pending_fn)
        return GLFW_FALSE;
    if (oengine_ltw_process_pending_fn() != 1)
    {
        _glfwInputError(GLFW_PLATFORM_ERROR, "O Engine LTW EGL: queued Surface transition failed");
        return GLFW_FALSE;
    }
    return GLFW_TRUE;
}
#endif
'''
if "oengine_get_ltw_share_info" not in text:
    if include_anchor not in text:
        raise SystemExit("egl_context.c internal include anchor changed")
    text = text.replace(include_anchor, include_replacement, 1)

create_anchor = '''    EGLNativeWindowType native;
    int index = 0;
'''
create_replacement = '''    EGLNativeWindowType native;
    int index = 0;
#if defined(_GLFW_ANDROID)
    OEngineLtwEglShareInfo oengine_ltw_share = {0};
    const GLFWbool oengine_use_ltw = oengine_get_ltw_share_info(&oengine_ltw_share);
#endif
'''
if "const GLFWbool oengine_use_ltw" not in text:
    if create_anchor not in text:
        raise SystemExit("EGL context creation local-variable anchor changed")
    text = text.replace(create_anchor, create_replacement, 1)

choose_anchor = '''    if (!chooseEGLConfig(ctxconfig, fbconfig, &config))
        return GLFW_FALSE;
'''
choose_replacement = '''#if defined(_GLFW_ANDROID)
    if (oengine_use_ltw)
    {
        _glfw.egl.display = (EGLDisplay) oengine_ltw_share.display;
        config = (EGLConfig) oengine_ltw_share.config;
    }
    else
#endif
    if (!chooseEGLConfig(ctxconfig, fbconfig, &config))
        return GLFW_FALSE;
'''
if "config = (EGLConfig) oengine_ltw_share.config;" not in text:
    if choose_anchor not in text:
        raise SystemExit("EGL config selection anchor changed")
    text = text.replace(choose_anchor, choose_replacement, 1)

context_anchor = '''    window->context.egl.handle = eglCreateContext(_glfw.egl.display,
                                                  config, share, attribs);
'''
context_replacement = '''#if defined(_GLFW_ANDROID)
    if (oengine_use_ltw)
        window->context.egl.handle = (EGLContext) oengine_ltw_share.context;
    else
#endif
        window->context.egl.handle = eglCreateContext(_glfw.egl.display,
                                                      config, share, attribs);
'''
if "window->context.egl.handle = (EGLContext) oengine_ltw_share.context;" not in text:
    if context_anchor not in text:
        raise SystemExit("EGL context creation call anchor changed")
    text = text.replace(context_anchor, context_replacement, 1)

surface_anchor = '''    if(_glfw.platform.platformID != GLFW_PLATFORM_ANDROID) {
        native = _glfw.platform.getEGLNativeWindow(window);
'''
surface_replacement = '''#if defined(_GLFW_ANDROID)
    if (oengine_use_ltw)
        window->context.egl.surface = (EGLSurface) oengine_ltw_share.window_surface;
    else
#endif
    if(_glfw.platform.platformID != GLFW_PLATFORM_ANDROID) {
        native = _glfw.platform.getEGLNativeWindow(window);
'''
if "window->context.egl.surface = (EGLSurface) oengine_ltw_share.window_surface;" not in text:
    if surface_anchor not in text:
        raise SystemExit("Android EGL window-surface creation anchor changed")
    text = text.replace(surface_anchor, surface_replacement, 1)

make_anchor = '''static void makeContextCurrentEGL(_GLFWwindow* window)
{
    if (window)
'''
make_replacement = '''static void makeContextCurrentEGL(_GLFWwindow* window)
{
#if defined(_GLFW_ANDROID)
    OEngineLtwEglShareInfo oengine_ltw_share = {0};
    if (oengine_get_ltw_share_info(&oengine_ltw_share))
    {
        /* The Android manager tracks surfaceInUse and switches to the LTW pbuffer
           on detach. Bypassing it lets nativeSurfaceDestroyed race EGL teardown. */
        if (!oengine_process_pending_ltw_window())
            return;
        if (window)
        {
            EGLSurface target = _glfwManageEglSurfaceAndroid(window);
            if (!target)
                return;
            window->context.egl.surface = target;
        }
        else
        {
            if (!oengine_get_ltw_share_info(&oengine_ltw_share))
                return;
            typedef EGLBoolean (*OEngineLtwMakeCurrentFn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
            OEngineLtwMakeCurrentFn make_current =
                (OEngineLtwMakeCurrentFn) oengine_ltw_share.make_current;
            EGLSurface target = (EGLSurface) oengine_ltw_share.pbuffer;
            if (!make_current || !make_current((EGLDisplay) oengine_ltw_share.display,
                                                target, target,
                                                (EGLContext) oengine_ltw_share.context))
            {
                _glfwInputError(GLFW_PLATFORM_ERROR, "O Engine LTW EGL: failed to make shared context current");
                return;
            }
        }
        _glfwPlatformSetTls(&_glfw.contextSlot, window);
        return;
    }
#endif
    if (window)
'''
if "O Engine LTW EGL: failed to make shared context current" not in text:
    if make_anchor not in text:
        raise SystemExit("EGL make-current function anchor changed")
    text = text.replace(make_anchor, make_replacement, 1)

swap_anchor = '''    if (window != _glfwPlatformGetTls(&_glfw.contextSlot))
    {
        _glfwInputError(GLFW_PLATFORM_ERROR,
                        "EGL: The context must be current on the calling thread when swapping buffers");
        return;
    }
'''
swap_replacement = swap_anchor + '''#if defined(_GLFW_ANDROID)
    OEngineLtwEglShareInfo oengine_ltw_share = {0};
    if (oengine_get_ltw_share_info(&oengine_ltw_share))
    {
        /* A resize/detach may arrive while the game is between make-current calls.
           Drain it here too, then let the Android manager update the current surface. */
        if (!oengine_process_pending_ltw_window())
            return;
        EGLSurface target = _glfwManageEglSurfaceAndroid(window);
        if (!target || !oengine_get_ltw_share_info(&oengine_ltw_share))
            return;
        typedef EGLBoolean (*OEngineLtwSwapBuffersFn)(EGLDisplay, EGLSurface);
        OEngineLtwSwapBuffersFn swap_buffers =
            (OEngineLtwSwapBuffersFn) oengine_ltw_share.swap_buffers;
        if (!swap_buffers || !oengine_ltw_share.window_surface ||
            !swap_buffers((EGLDisplay) oengine_ltw_share.display,
                          (EGLSurface) oengine_ltw_share.window_surface))
            _glfwInputError(GLFW_PLATFORM_ERROR, "O Engine LTW EGL: frame presentation failed");
        return;
    }
#endif
'''
if "O Engine LTW EGL: frame presentation failed" not in text:
    if swap_anchor not in text:
        raise SystemExit("EGL swap-buffers function anchor changed")
    text = text.replace(swap_anchor, swap_replacement, 1)

destroy_surface_anchor = '''    if (window->context.egl.surface)
    {
        eglDestroySurface(_glfw.egl.display, window->context.egl.surface);
        window->context.egl.surface = EGL_NO_SURFACE;
    }
'''
destroy_surface_replacement = '''    if (window->context.egl.surface)
    {
#if defined(_GLFW_ANDROID)
        OEngineLtwEglShareInfo oengine_ltw_share = {0};
        const GLFWbool is_ltw_surface = oengine_get_ltw_share_info(&oengine_ltw_share) &&
            window->context.egl.handle == (EGLContext) oengine_ltw_share.context;
        if (!is_ltw_surface)
#endif
            eglDestroySurface(_glfw.egl.display, window->context.egl.surface);
        window->context.egl.surface = EGL_NO_SURFACE;
    }
'''
if "const GLFWbool is_ltw_surface" not in text:
    if destroy_surface_anchor not in text:
        raise SystemExit("EGL destroy-surface anchor changed")
    text = text.replace(destroy_surface_anchor, destroy_surface_replacement, 1)

destroy_context_anchor = '''    if (window->context.egl.handle)
    {
        eglDestroyContext(_glfw.egl.display, window->context.egl.handle);
        window->context.egl.handle = EGL_NO_CONTEXT;
    }
'''
destroy_context_replacement = '''    if (window->context.egl.handle)
    {
#if defined(_GLFW_ANDROID)
        OEngineLtwEglShareInfo oengine_ltw_share = {0};
        const GLFWbool is_ltw_context = oengine_get_ltw_share_info(&oengine_ltw_share) &&
            window->context.egl.handle == (EGLContext) oengine_ltw_share.context;
        if (!is_ltw_context)
#endif
            eglDestroyContext(_glfw.egl.display, window->context.egl.handle);
        window->context.egl.handle = EGL_NO_CONTEXT;
    }
'''
if "const GLFWbool is_ltw_context" not in text:
    if destroy_context_anchor not in text:
        raise SystemExit("EGL destroy-context anchor changed")
    text = text.replace(destroy_context_anchor, destroy_context_replacement, 1)

terminate_anchor = '''void _glfwTerminateEGL(void)
{
    if (_glfw.egl.display)
    {
        eglTerminate(_glfw.egl.display);
        _glfw.egl.display = EGL_NO_DISPLAY;
    }
'''
terminate_replacement = '''void _glfwTerminateEGL(void)
{
    if (_glfw.egl.display)
    {
#if defined(_GLFW_ANDROID)
        OEngineLtwEglShareInfo oengine_ltw_share = {0};
        const GLFWbool display_is_ltw_owned = oengine_get_ltw_share_info(&oengine_ltw_share) &&
            _glfw.egl.display == (EGLDisplay) oengine_ltw_share.display;
        if (!display_is_ltw_owned)
#endif
            eglTerminate(_glfw.egl.display);
        _glfw.egl.display = EGL_NO_DISPLAY;
    }
'''
if "display_is_ltw_owned" not in text:
    if terminate_anchor not in text:
        raise SystemExit("EGL terminate anchor changed")
    text = text.replace(terminate_anchor, terminate_replacement, 1)

init_anchor = '''GLFWbool _glfwInitEGL(void)
{
    int i;
    EGLint* attribs = NULL;
    const char* extensions;
'''
init_replacement = init_anchor + '''#if defined(_GLFW_ANDROID)
    OEngineLtwEglShareInfo oengine_init_share = {0};
    const GLFWbool oengine_init_uses_ltw = oengine_get_ltw_share_info(&oengine_init_share);
#endif
'''
if "oengine_init_uses_ltw" not in text:
    if init_anchor not in text:
        raise SystemExit("EGL initialization function anchor changed")
    text = text.replace(init_anchor, init_replacement, 1)

display_anchor = '''    _glfw.egl.platform = _glfw.platform.getEGLPlatform(&attribs);
    if (_glfw.egl.platform)
    {
        _glfw.egl.display =
            eglGetPlatformDisplayEXT(_glfw.egl.platform,
                                     _glfw.platform.getEGLNativeDisplay(),
                                     attribs);
    }
    else
        _glfw.egl.display = eglGetDisplay(_glfw.platform.getEGLNativeDisplay());

    _glfw_free(attribs);
'''
display_replacement = '''#if defined(_GLFW_ANDROID)
    if (oengine_init_uses_ltw)
        _glfw.egl.display = (EGLDisplay) oengine_init_share.display;
    else
#endif
    {
        _glfw.egl.platform = _glfw.platform.getEGLPlatform(&attribs);
        if (_glfw.egl.platform)
        {
            _glfw.egl.display =
                eglGetPlatformDisplayEXT(_glfw.egl.platform,
                                         _glfw.platform.getEGLNativeDisplay(),
                                         attribs);
        }
        else
            _glfw.egl.display = eglGetDisplay(_glfw.platform.getEGLNativeDisplay());
    }

    _glfw_free(attribs);
'''
if "oengine_init_share.display" not in text:
    if display_anchor not in text:
        raise SystemExit("EGL display selection anchor changed")
    text = text.replace(display_anchor, display_replacement, 1)

initialize_anchor = '''    if (!eglInitialize(_glfw.egl.display, &_glfw.egl.major, &_glfw.egl.minor))
    {
        _glfwInputError(GLFW_API_UNAVAILABLE,
                        "EGL: Failed to initialize EGL: %s",
                        getEGLErrorString(eglGetError()));

        _glfwTerminateEGL();
        return GLFW_FALSE;
    }
'''
initialize_replacement = '''#if defined(_GLFW_ANDROID)
    if (oengine_init_uses_ltw)
    {
        const char* egl_version = eglQueryString(_glfw.egl.display, 0x3054 /* EGL_VERSION */);
        if (egl_version)
            sscanf(egl_version, "%d.%d", &_glfw.egl.major, &_glfw.egl.minor);
    }
    else
#endif
    if (!eglInitialize(_glfw.egl.display, &_glfw.egl.major, &_glfw.egl.minor))
    {
        _glfwInputError(GLFW_API_UNAVAILABLE,
                        "EGL: Failed to initialize EGL: %s",
                        getEGLErrorString(eglGetError()));

        _glfwTerminateEGL();
        return GLFW_FALSE;
    }
'''
if "oengine_init_uses_ltw)" not in text:
    if initialize_anchor not in text:
        raise SystemExit("EGL initialize call anchor changed")
    text = text.replace(initialize_anchor, initialize_replacement, 1)


# Mojo's Android platform has its own surface-mode switcher in android_window.c.
# Replace that path too: otherwise it would create/destroy a second EGLSurface
# even though egl_context.c has been taught to borrow O Engine's LTW context.
android_source = Path("native/upstream/mojo-glfw/src/android_window.c")
android_text = android_source.read_text(encoding="utf-8")
android_include = '#include "internal.h"\n'
android_include_replacement = android_include + '#include "oengine_ltw_egl.h"\n#include <dlfcn.h>\n'
if "oengine_ltw_egl.h" not in android_text:
    if android_include not in android_text:
        raise SystemExit("Pinned Mojo android_window.c include anchor changed")
    android_text = android_text.replace(android_include, android_include_replacement, 1)

android_helper = r'''
typedef uint32_t (*OEngineAndroidActiveShareInfoFn)(OEngineLtwEglShareInfo*);
static void* oengine_android_ltw_handle = NULL;
static OEngineAndroidActiveShareInfoFn oengine_android_ltw_share_info_fn = NULL;

static GLFWbool oengine_android_get_ltw_share_info(OEngineLtwEglShareInfo* info)
{
    if (!oengine_android_ltw_share_info_fn)
    {
        if (!oengine_android_ltw_handle)
            oengine_android_ltw_handle = dlopen("liboengine_native.so", RTLD_NOW | RTLD_LOCAL);
        if (oengine_android_ltw_handle)
            oengine_android_ltw_share_info_fn = (OEngineAndroidActiveShareInfoFn)
                dlsym(oengine_android_ltw_handle, "oengine_ltw_egl_active_share_info");
    }
    return oengine_android_ltw_share_info_fn && info &&
        oengine_android_ltw_share_info_fn(info) == 1;
}
'''
if "oengine_android_get_ltw_share_info" not in android_text:
    android_anchor = "static struct ANativeWindow* nativeWindow = NULL;\n"
    if android_anchor not in android_text:
        raise SystemExit("Pinned Mojo Android native-window global anchor changed")
    android_text = android_text.replace(android_anchor, android_anchor + android_helper, 1)

manage_anchor = '''// Select a new EGLSurface
EGLSurface _glfwManageEglSurfaceAndroid(_GLFWwindow* window) {
'''
manage_end = '''// Check if the surface has been changed before attempting to swap buffers
'''
start = android_text.find(manage_anchor)
end = android_text.find(manage_end, start)
if start < 0 or end < 0:
    raise SystemExit("Pinned Mojo Android EGL surface manager anchor changed")
manage_replacement = r'''// Select the LTW-owned EGLSurface; O Engine owns all EGL object lifetimes.
EGLSurface _glfwManageEglSurfaceAndroid(_GLFWwindow* window) {
    OEngineLtwEglShareInfo share = {0};
    if (!oengine_android_get_ltw_share_info(&share))
    {
        _glfwInputError(GLFW_PLATFORM_ERROR, "O Engine LTW EGL session is unavailable");
        return EGL_NO_SURFACE;
    }

    /* Pair the availability check and surfaceInUse transition with the destroy
       waiter. This prevents nativeSurfaceDestroyed from releasing nativeWindow
       between our check and the EGL thread beginning to use the window surface. */
    pthread_mutex_lock(&nw_egl_mutex);
    int wantedMode = (window == surfaceOwner && !surfaceDestroyed && nativeWindow)
        ? GLFW_ANDROID_WINDOW_MODE_SURFACE
        : GLFW_ANDROID_WINDOW_MODE_PBUFFER;
    if (wantedMode == GLFW_ANDROID_WINDOW_MODE_SURFACE)
        surfaceInUse = true;
    pthread_mutex_unlock(&nw_egl_mutex);

    int currentMode = window->android.mode;
    EGLSurface target = wantedMode == GLFW_ANDROID_WINDOW_MODE_SURFACE
        ? (EGLSurface) share.window_surface
        : (EGLSurface) share.pbuffer;

    /* A Surface event can replace the EGLSurface without changing GLFW's mode. */
    if (wantedMode == GLFW_ANDROID_WINDOW_MODE_SURFACE && !target)
    {
        wantedMode = GLFW_ANDROID_WINDOW_MODE_PBUFFER;
        target = (EGLSurface) share.pbuffer;
        pthread_mutex_lock(&nw_egl_mutex);
        surfaceInUse = false;
        pthread_cond_broadcast(&nw_egl_cond);
        pthread_mutex_unlock(&nw_egl_mutex);
    }
    if (!target || !share.make_current)
    {
        if (wantedMode == GLFW_ANDROID_WINDOW_MODE_SURFACE && currentMode != GLFW_ANDROID_WINDOW_MODE_SURFACE) {
            pthread_mutex_lock(&nw_egl_mutex);
            surfaceInUse = false;
            pthread_cond_broadcast(&nw_egl_cond);
            pthread_mutex_unlock(&nw_egl_mutex);
        }
        _glfwInputError(GLFW_PLATFORM_ERROR, "O Engine LTW EGL has no usable window/pbuffer surface");
        return EGL_NO_SURFACE;
    }

    typedef EGLBoolean (*OEngineAndroidMakeCurrentFn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
    OEngineAndroidMakeCurrentFn make_current =
        (OEngineAndroidMakeCurrentFn) share.make_current;
    if (!make_current((EGLDisplay) share.display, target, target, (EGLContext) share.context))
    {
        if (wantedMode == GLFW_ANDROID_WINDOW_MODE_SURFACE) {
            pthread_mutex_lock(&nw_egl_mutex);
            surfaceInUse = false;
            pthread_cond_broadcast(&nw_egl_cond);
            pthread_mutex_unlock(&nw_egl_mutex);
        }
        _glfwInputError(GLFW_PLATFORM_ERROR, "O Engine LTW eglMakeCurrent failed for Android surface mode");
        return EGL_NO_SURFACE;
    }

    if (currentMode == GLFW_ANDROID_WINDOW_MODE_SURFACE &&
        wantedMode != GLFW_ANDROID_WINDOW_MODE_SURFACE)
    {
        pthread_mutex_lock(&nw_egl_mutex);
        surfaceInUse = false;
        pthread_cond_broadcast(&nw_egl_cond);
        pthread_mutex_unlock(&nw_egl_mutex);
    }

    if (wantedMode == GLFW_ANDROID_WINDOW_MODE_SURFACE)
    {
        GLFWbool did_update = atomic_exchange_explicit(&surfaceUpdated, GLFW_FALSE, memory_order_seq_cst);
        if (nativeWindow && did_update)
            updateNativeWindowDimensions(window);
    }

    window->android.mode = wantedMode;
    window->context.egl.surface = target;
    return target;
}

'''
android_text = android_text[:start] + manage_replacement + android_text[end:]

if "eglCreateWindowSurface(display, config, nativeWindow" in android_text[android_text.find(manage_replacement[:50]):]:
    raise SystemExit("Android EGL surface manager still creates its own native window surface")
if "eglDestroySurface(_glfw.egl.display, oldSurface)" in android_text[android_text.find(manage_replacement[:50]):]:
    raise SystemExit("Android EGL surface manager still destroys LTW-owned surfaces")
if "oengine_android_get_ltw_share_info(&share)" not in android_text:
    raise SystemExit("Android GLFW surface manager is not using LTW share-info")
if "surfaceInUse = false;\n        pthread_cond_broadcast(&nw_egl_cond);" not in android_text:
    raise SystemExit("Android GLFW must clear surfaceInUse before waking nativeSurfaceDestroyed")
destroy_handler = "Java_git_artdeell_dnbootstrap_glfw_GLFW_nativeSurfaceDestroyed"
destroy_function_start = android_text.find(destroy_handler)
destroy_body_start = android_text.find("{", destroy_function_start)
if destroy_function_start < 0 or destroy_body_start < 0:
    raise SystemExit("Pinned Mojo nativeSurfaceDestroyed function changed")
# The input patch runs first and may already have rewritten this JNI handler.
# Find its closing brace by balancing braces, not by assuming blank-line spacing.
depth = 0
destroy_body_end = -1
for position in range(destroy_body_start, len(android_text)):
    if android_text[position] == "{":
        depth += 1
    elif android_text[position] == "}":
        depth -= 1
        if depth == 0:
            destroy_body_end = position
            break
if destroy_body_end < 0:
    raise SystemExit("Could not locate the end of nativeSurfaceDestroyed")
destroy_handler_text = android_text[destroy_body_start + 1:destroy_body_end]
if "while (surfaceInUse)" not in destroy_handler_text:
    destroy_function_body = """
    pthread_mutex_lock(&nw_egl_mutex);
    surfaceDestroyed = true;
    pthread_mutex_unlock(&nw_egl_mutex);
    /* Wake the GLFW owner thread after the detach has been queued. */
    _glfwPostEmptyEventAndroid();
    if (!ownedByVulkan) {
        pthread_mutex_lock(&nw_egl_mutex);
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
"""
    android_text = android_text[:destroy_body_start + 1] + destroy_function_body + android_text[destroy_body_end:]
elif "_glfwPostEmptyEventAndroid();" not in destroy_handler_text:
    android_text = android_text[:destroy_body_start + 1] + destroy_handler_text + "\n    _glfwPostEmptyEventAndroid();\n" + android_text[destroy_body_end:]

if "pthread_mutex_lock(&nw_egl_mutex);" not in manage_replacement or "surfaceInUse = true;" not in manage_replacement:
    raise SystemExit("Android GLFW must protect nativeWindow use with the Surface condition mutex")
if "atomic_exchange_explicit(&surfaceUpdated" not in manage_replacement:
    raise SystemExit("Android GLFW must consume Surface resize notifications without losing concurrent updates")
destroy_anchor = """surfaceDestroyed = true;
    if(!ownedByVulkan && surfaceInUse) {"""
destroy_replacement = """surfaceDestroyed = true;
    /* Wake the GLFW owner thread after the detach has been queued. */
    _glfwPostEmptyEventAndroid();
    if(!ownedByVulkan && surfaceInUse) {"""
if "_glfwPostEmptyEventAndroid();" not in android_text:
    if destroy_anchor not in android_text:
        raise SystemExit("Pinned Mojo nativeSurfaceDestroyed wake anchor changed")
    android_text = android_text.replace(destroy_anchor, destroy_replacement, 1)
android_source.write_text(android_text, encoding="utf-8")

required = [
    "oengine_get_ltw_share_info",
    "window->context.egl.handle = (EGLContext) oengine_ltw_share.context;",
    "window->context.egl.surface = (EGLSurface) oengine_ltw_share.window_surface;",
    "O Engine LTW EGL: failed to make shared context current",
    "O Engine LTW EGL: frame presentation failed",
    "oengine_ltw_egl_process_pending_window",
    "oengine_process_pending_ltw_window()",
    "_glfwManageEglSurfaceAndroid(window)",
    "const GLFWbool is_ltw_context",
    "const GLFWbool is_ltw_surface",
    "display_is_ltw_owned",
]
for item in required:
    if item not in text:
        raise SystemExit("LTW shared EGL integration invariant missing: " + item)

source.write_text(text, encoding="utf-8")
print("Patched Mojo GLFW to borrow O Engine LTW EGL context, surface, make-current and swap path.")
