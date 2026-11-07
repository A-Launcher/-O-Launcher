# Mojo Android GLFW backend audit

This document records the source-level audit used to choose an Android GLFW integration path. It is not a claim that the upstream backend has been copied or integrated into O Engine.

## Exact source and provenance

- MojoLauncher application: https://github.com/MojoLauncher/MojoLauncher
- Application branch: `v3_openjdk`
- Inspected application commit: `1b8d90424c1cb791321d45f60ac62c54f4b013fa`
- Native GLFW repository: https://github.com/MojoLauncher/glfw
- Exact GLFW submodule commit pinned by that application tree: `7a6c6ceb51f20cd0db84dec2914ac1a0237051f6`
- GLFW CMake project version at that revision: 3.5.0
- `glfw/LICENSE.md` contains the GLFW zlib-style license. Preserve the upstream notice and clearly mark any modified source if this code is adapted.
- Mojo's Java-side backend is in `app_mojo/src/main/java/git/artdeell/mojo/game/platform/backend/GLFWBackend.java`; its JNI binding class is in the GLFW repository at `jni_bindings/src/main/java/git/artdeell/dnbootstrap/glfw/GLFW.java`.

## Confirmed native event-poll contract

At the pinned GLFW commit:

- `src/android_init.c` registers the Android platform functions, including `_glfwPollEventsAndroid`, `_glfwWaitEventsAndroid`, `_glfwCreateWindowAndroid`, and `_glfwGetEGLNativeWindowAndroid`.
- `src/android_window.c::_glfwPollEventsAndroid` calls `process_flag_bits()`, drains the native input queue through `android_dequeue_event`, and applies changed native-window dimensions.
- `android_dequeue_event` converts queued event types into GLFW callbacks: `_glfwInputKey`, `_glfwInputChar`, `_glfwInputMouseClick`, and `_glfwInputScroll`. Cursor position is delivered from the pending flag path.
- `src/egl_context.c::makeContextCurrentEGL` calls `_glfwManageEglSurfaceAndroid` before `eglMakeCurrent` on Android.
- `src/egl_context.c::swapBuffersEGL` calls `_glfwSwapBuffersAttentionEglAndroid` before `eglSwapBuffers`. This is the actual GLFW presentation path, not a detached helper.
- `src/android_window.c` handles the native Surface lifecycle through JNI exports for `nativeSurfaceCreated(Surface)`, `nativeSurfaceUpdated()`, and `nativeSurfaceDestroyed()`. It acquires/releases `ANativeWindow` references and coordinates destruction with active EGL use.

## Java-to-native ABI observed

The Java binding class is `git.artdeell.dnbootstrap.glfw.GLFW`. It loads `libglfw` and calls `initialize()`. Its native methods include:

- `nativeSurfaceCreated(Surface)`, `nativeSurfaceUpdated()`, `nativeSurfaceDestroyed()`
- `sendMousePosition0(double,double)`
- `sendRawKeyEvent(int,int,int,char)` returning boolean
- `sendKeyEvent(int,int,int)`
- `sendMouseEvent(int,int,int)`
- `sendBulkUnicodeEvent(String,int)`
- `sendScrollEvent(double,double)`
- `nativeSetWindowAttribs(int,boolean)`
- `nativeNotifyGamepadConnected()`

Mojo's `GLFWBackend` forwards Surface lifecycle, key, mouse, scroll, text, visibility, and hover events to this ABI. Its Surface provider reports create, resize, and destroy separately.

## Important adaptation blockers

1. The native fork is not a drop-in library for O Engine. It has Mojo-specific dependencies: `mojoexec.h`, `mojoexec_renderspec`, `mojoexec_make_bigcore_affine()`, and `mojoexec_acq_egl_handle()` / `mojoexec_acq_vulkan_handle()`. These must be replaced by explicit O Engine interfaces and verified renderer configuration.
2. The Android backend integrates into GLFW's own EGL context lifecycle. O Engine currently has an LTW-owned EGL session. Building this backend unchanged would risk creating a second, competing EGL context. The integration must either make GLFW/LTW share one context and one surface lifecycle, or deliberately migrate ownership; it must not keep two independent contexts and assume Minecraft will render.
3. The upstream native input queue in `src/android_input_queue.c` has two 512-event buffers, but `_input_queue_push` increments `events_total` and indexes the fixed array without a capacity check. Do not copy this queue unchanged. O Engine's bounded queue and reset-on-transition-overflow behavior are safer starting points.
4. The fork's native queue currently accepts its own native event structs. O Engine's new Binder ingress and `OEngineGameInputBridge.drainForGameThread()` form a typed Kotlin-side handoff, but no JNI call from `_glfwPollEventsAndroid` to that drain API exists yet.
5. The GLFW fork's touch model is primarily cursor/mouse-oriented. O Engine must make an explicit touch-to-mouse policy and preserve multi-pointer semantics where appropriate rather than pretending the GLFW mouse ABI represents native multitouch.
6. The upstream backend's `android_window.c` and `egl_context.c` need a reviewed integration with LTW's EGL wrappers and Android Surface generations before packaging `libglfw.so`.

## O Engine decision

Use this pinned GLFW Android backend as the implementation reference because its native poll and swap paths are now identified. Adapt the platform backend and JNI boundary only after the LTW context ownership design is settled. Keep O Engine's bounded queue, digital-control tracking, and reset behavior; do not transplant the upstream fixed-buffer queue. No actual Minecraft GLFW event delivery or rendered frame is claimed by this audit.
