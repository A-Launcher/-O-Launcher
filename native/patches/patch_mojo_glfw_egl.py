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
static void* oengine_ltw_handle = NULL;
static OEngineActiveShareInfoFn oengine_ltw_share_info_fn = NULL;

static GLFWbool oengine_get_ltw_share_info(OEngineLtwEglShareInfo* info)
{
    if (!oengine_ltw_share_info_fn)
    {
        if (!oengine_ltw_handle)
            oengine_ltw_handle = dlopen("liboengine_native.so", RTLD_NOW | RTLD_LOCAL);
        if (oengine_ltw_handle)
            oengine_ltw_share_info_fn = (OEngineActiveShareInfoFn)
                dlsym(oengine_ltw_handle, "oengine_ltw_egl_active_share_info");
    }
    return oengine_ltw_share_info_fn && info &&
        oengine_ltw_share_info_fn(info) == 1;
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
        typedef EGLBoolean (*OEngineLtwMakeCurrentFn)(EGLDisplay, EGLSurface, EGLSurface, EGLContext);
        OEngineLtwMakeCurrentFn make_current =
            (OEngineLtwMakeCurrentFn) oengine_ltw_share.make_current;
        EGLSurface target = window
            ? (EGLSurface) oengine_ltw_share.window_surface
            : (EGLSurface) oengine_ltw_share.pbuffer;
        if (!make_current || !make_current((EGLDisplay) oengine_ltw_share.display,
                                            target, target,
                                            (EGLContext) oengine_ltw_share.context))
        {
            _glfwInputError(GLFW_PLATFORM_ERROR, "O Engine LTW EGL: failed to make shared context current");
            return;
        }
        if (window)
            window->context.egl.surface = target;
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
            window->context.egl.surface == (EGLSurface) oengine_ltw_share.window_surface;
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

required = [
    "oengine_get_ltw_share_info",
    "window->context.egl.handle = (EGLContext) oengine_ltw_share.context;",
    "window->context.egl.surface = (EGLSurface) oengine_ltw_share.window_surface;",
    "O Engine LTW EGL: failed to make shared context current",
    "O Engine LTW EGL: frame presentation failed",
    "const GLFWbool is_ltw_context",
    "const GLFWbool is_ltw_surface",
    "display_is_ltw_owned",
]
for item in required:
    if item not in text:
        raise SystemExit("LTW shared EGL integration invariant missing: " + item)

source.write_text(text, encoding="utf-8")
print("Patched Mojo GLFW to borrow O Engine LTW EGL context, surface, make-current and swap path.")
