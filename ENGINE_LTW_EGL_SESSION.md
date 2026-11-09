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
5. Initial window attach, replacement/resize, detach, swap, context destruction, and native library lifetime are handled by one thread-confined native session. Later Surface events enter through a Rust queue and are applied only on the LTW/GLFW owner thread.
6. Kotlin owns Android IPC and Surface callbacks; Rust owns native-window references, queued Surface transitions, EGL surfaces, and LTW context-wrapper calls.

## Explicit remaining work

This is not yet a complete Android GLFW implementation. The following still need to be implemented and tested:

- Full Android GLFW window/monitor behavior and cursor/clipboard/gamepad callbacks.
- End-to-end confirmation that Minecraft's downloaded LWJGL Java bindings call the packaged Mojo native functions as expected.
- Physical-device rendering tests, including surface recreation, resize, detach/reattach, app pause/resume, and frame presentation.
- A successful end-to-end Minecraft launch.

No Pojav or Mojo launcher source files were copied into O Engine for this milestone. Their LGPL-3.0 licensing and the pinned LTW LGPL-3.0 obligations remain documented in `ENGINE_UPSTREAM_COMPONENTS.md`.


## Native GLFW context-sharing contract

The Rust engine now exports `oengine_ltw_egl_share_info(session, output)`, declared in `native/oengine/include/oengine_ltw_egl.h`. It returns a borrowed view of the active session's EGL display, config, tracked context, pbuffer/window surfaces, and LTW-wrapped create/make-current/destroy/swap function pointers. The call is rejected unless it runs on the session's creating game thread. The returned pointers remain valid only while the session is alive and must never be used from the Android UI or Binder ingress thread.

This is an integration seam, not a second EGL session and not a renderer by itself. It exists so the Android GLFW port can create any additional context through LTW's wrappers and share the tracked resource group, rather than calling Android EGL directly and leaving LTW's context map unaware. Rust layout tests and an Android CI C-header compile check pin the 10-pointer ABI; APK symbol verification requires the exported function for every supported ABI.

**New integration step:** `native/patches/patch_mojo_glfw_egl.py` now patches the pinned Mojo GLFW EGL backend to resolve `oengine_ltw_egl_active_share_info` from `liboengine_native.so`. It reuses the existing LTW display/config/context/window surface, routes make-current and swap through LTW's function pointers, and avoids destroying or terminating LTW-owned EGL objects. The Android CI workflow applies this patch after building the Rust library and checks that the active-share export is present in the APK. This is source-level integration awaiting CI confirmation and physical-device validation; it is not yet proof of a Minecraft frame.


## Live Surface ingress and GLFW-thread application

After Minecraft main is dispatched, the JVM owner HandlerThread is no longer available to process Binder messages. O Engine therefore exposes a second Messenger backed by `OEngineSurfaceIngress`. It accepts Surface attach/detach events while Minecraft is running, acquires an ANativeWindow reference, and places the transition in Rust's pending queue without making any EGL calls on the Binder thread.

The pinned Mojo Android GLFW event-poll adapter calls `oengine_ltw_egl_process_pending_window()` on the GLFW/LTW owner thread. Rust applies the event there: it switches to the tracked pbuffer, destroys the previous window surface, creates and binds the replacement through the LTW EGL entry points, updates the GL viewport, and releases superseded native-window references. Detach switches to pbuffer and releases the active ANativeWindow. Queue replacement and session teardown also release references that have not yet been applied.

Mojo's Android-specific `_glfwManageEglSurfaceAndroid` is patched to borrow the current LTW pbuffer/window surface and use the LTW `eglMakeCurrent` wrapper. It no longer creates or destroys a second EGL surface. Android Surface create/update/destroy callbacks push empty GLFW events to wake a thread in `glfwWaitEvents`; the GLFW poll adapter applies surface-mode changes and signals Mojo's `nw_egl_cond` so `nativeSurfaceDestroyed` can complete. The service sends a second wake after enqueueing detach so Rust can release the LTW window reference.

This is source-level integration. Android CI must validate the patched upstream sources and packaged ABIs, and physical-device testing is still required to prove the actual Minecraft/LWJGL event loop and rendered frames.


## Current render-bootstrap checkpoint (2026-11-07)

The Android runtime prerequisites have advanced beyond the earlier historical checkpoints above:

- The APK now packages ABI-matched Android `liblwjgl.so` and `liblwjgl_opengl.so` (plus optional NanoVG/STB/TinyFD modules) from the pinned PojavLauncher source commit. CI verifies the upstream Git blob SHA for every library and ABI. The game process checks that the required core/OpenGL modules exist before HotSpot creation.
- O Engine now declares and calls the exact MojoExec JNI `prepareEgl` and `setNativeLibraryDir` APIs before GLFW initialization. This sets Mojo's EGL handle and forces its Android context configuration to GLES 3; loading `libglfw.so` alone did not do that.
- Before Minecraft main, the HotSpot hook preflight initializes `MemoryAccessJNI`, checks the native pointer width, initializes LWJGL's OpenGL module/renderer alias, then calls `GLFW.glfwGetVersionString()` through the generated LWJGL 3.3.3 function table. A failed core native load, OpenGL module load, LTW alias redirect, or GLFW C-library load now blocks launch with a diagnostic instead of being mistaken for a ready renderer.
- The `ndlopen` alias hook now opens a second reference to the retained LTW path for LWJGL. LWJGL may safely call `dlclose` on its own handle without unloading the renderer reference owned by the EGL session.
- Mojo's Surface condition mutexes are statically initialized for callbacks before `glfwInit` and after `glfwTerminate`. The Surface manager reserves `surfaceInUse` under the same mutex as the destroy waiter, consumes resize notifications atomically, and wakes waiters after a successful switch to pbuffer.

These checks prove more of the JNI/library bootstrap, not a displayed Minecraft frame. The remaining decisive runtime steps are `glfwInit`, `glfwCreateWindow`, making the LTW context current through GLFW, creating LWJGL GL capabilities, drawing/swapping a frame, and validating Surface recreation on a physical Android device. The earlier bullet list under "Explicit remaining work" is a historical snapshot and should be read together with this checkpoint.


## Per-window GLFW context ownership correction

The GLFW adapter must create a distinct per-window EGL context through LTW's wrapped `eglCreateContext`, sharing resources with the LTW root context. It binds that per-window context through LTW's wrapped `eglMakeCurrent` and destroys it through LTW's wrapped `eglDestroyContext`. The LTW-owned display and window/pbuffer surfaces remain borrowed; GLFW must not terminate the display or destroy those surfaces. Reusing the root context directly as GLFW's window context is incorrect because GLFW requires distinct per-window context handles and performs its own current-context bookkeeping.
