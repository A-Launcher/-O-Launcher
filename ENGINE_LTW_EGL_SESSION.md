# O Engine LTW EGL session

## Source review

This integration was designed after reviewing the relevant source trees and implementations:

- PojavLauncher `v3_openjdk`: `egl_bridge.c`, `ctxbridges/egl_loader.c`, `MinecraftGLSurface.java`, and its LWJGL `CallbackBridge.java`.
- MojoLauncher `v3_openjdk`: `SurfaceProvider`, `SurfaceViewSurfaceProvider`, `PlatformBackend`, `GLFWBackend`, `GameRenderer`, and `GLESRenderSpec`.
- LTW pinned at `3f1f4df6c80c925ea34067b1d943a84acce0184d`: `egl.c`, `egl.h`, and `proc.c`.

Pojav demonstrates that Android Surface ownership, ANativeWindow references, EGL window/context lifecycle, swap, and GLFW/input callbacks form one connected platform bridge. Mojo makes the Surface lifecycle and GLFW platform backend explicit, and configures the renderer environment before JVM startup.

## Implemented in O Engine

1. The game process loads and retains `libltw.so` before EGL session creation.
2. Rust resolves host EGL entry points from Android `libEGL.so` and LTW's exported `eglCreateContext`, `eglMakeCurrent`, and `eglDestroyContext` wrappers from the retained renderer handle.
3. The context is created and first made current through LTW. This registers it in LTW's per-context compatibility map.
4. The session creates a pbuffer for initialization, then creates and binds a window surface from the retained `ANativeWindow` reference when Android supplies the game Surface.
5. Window replacement/resize, detach, swap, context destruction, and native library lifetime are handled by one thread-confined native session.
6. Kotlin owns the Android IPC and lifecycle; Rust owns EGL and the LTW context-wrapper calls.

## Explicit remaining work

This is not yet a complete Android GLFW implementation. The following still need to be implemented and tested:

- Android GLFW window creation/destruction and monitor/window-size callbacks.
- Keyboard, mouse, touch, text, and gamepad event delivery into LWJGL callbacks.
- Wiring Minecraft's actual GLFW swap loop to the native EGL presentation path.
- Physical-device rendering tests, including surface recreation and app pause/resume.
- A successful end-to-end Minecraft launch.

No Pojav or Mojo launcher source files were copied into O Engine for this milestone. Their LGPL-3.0 licensing and the pinned LTW LGPL-3.0 obligations remain documented in `ENGINE_UPSTREAM_COMPONENTS.md`.
