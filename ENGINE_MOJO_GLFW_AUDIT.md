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

## Two distinct Java/native boundaries

Mojo's ART-side Android binding class is `git.artdeell.dnbootstrap.glfw.GLFW`. It loads `libglfw` and calls `initialize()`. Its native methods include:

- `nativeSurfaceCreated(Surface)`, `nativeSurfaceUpdated()`, `nativeSurfaceDestroyed()`
- `sendMousePosition0(double,double)`
- `sendRawKeyEvent(int,int,int,char)` returning boolean
- `sendKeyEvent(int,int,int)`
- `sendMouseEvent(int,int,int)`
- `sendBulkUnicodeEvent(String,int)`
- `sendScrollEvent(double,double)`
- `nativeSetWindowAttribs(int,boolean)`
- `nativeNotifyGamepadConnected()`

Mojo's `GLFWBackend` forwards Surface lifecycle, key, mouse, scroll, text, visibility, and hover events to this ART JNI ABI. Its Surface provider reports create, resize, and destroy separately.

Minecraft's embedded HotSpot uses a different LWJGL 3 boundary: the generated `org.lwjgl.glfw.GLFW` class loads the configured GLFW shared library, resolves exported C functions such as `glfwInit`, `glfwCreateWindow`, `glfwPollEvents`, and `glfwSwapBuffers` into its function table, then invokes those function pointers. It does **not** require a separate `Java_org_lwjgl_glfw_GLFW_*` JNI export for every GLFW API. O Engine's `org.lwjgl.glfw.libname` configuration and pinned C-symbol contract are therefore the correct kind of connection for the GLFW module. The separate LWJGL core native library and its HotSpot JNI loader hooks remain separate runtime requirements.

The ART shim in O Engine is the platform callback endpoint for Mojo's Android native port; it must not be confused with the HotSpot LWJGL class. O Engine has adapted Surface lifecycle, clipboard, pointer capture, and input ingress here, and now adapts custom cursor images into Android `PointerIcon` instances. The upstream cursor buffer contract is copied only at the API boundary; O Engine owns its view lifecycle.

## Important adaptation blockers

1. The native fork is not a drop-in library for O Engine. It has Mojo-specific dependencies: `mojoexec.h`, `mojoexec_renderspec`, `mojoexec_make_bigcore_affine()`, and `mojoexec_acq_egl_handle()` / `mojoexec_acq_vulkan_handle()`. These must be replaced by explicit O Engine interfaces and verified renderer configuration.
2. The Android backend integrates into GLFW's own EGL context lifecycle. O Engine currently has an LTW-owned EGL session. Building this backend unchanged would risk creating a second, competing EGL context. The integration must either make GLFW/LTW share one context and one surface lifecycle, or deliberately migrate ownership; it must not keep two independent contexts and assume Minecraft will render.
3. The upstream native input queue in `src/android_input_queue.c` has two 512-event buffers, but `_input_queue_push` increments `events_total` and indexes the fixed array without a capacity check. Do not copy this queue unchanged. O Engine's bounded queue and reset-on-transition-overflow behavior are safer starting points.
4. The fork's native queue currently accepts its own native event structs. O Engine's new Binder ingress and `OEngineGameInputBridge.drainForGameThread()` form a typed Kotlin-side handoff, but no JNI call from `_glfwPollEventsAndroid` to that drain API exists yet.
5. The GLFW fork's touch model is primarily cursor/mouse-oriented. O Engine must make an explicit touch-to-mouse policy and preserve multi-pointer semantics where appropriate rather than pretending the GLFW mouse ABI represents native multitouch.
6. The upstream backend's `android_window.c` and `egl_context.c` need a reviewed integration with LTW's EGL wrappers and Android Surface generations before packaging `libglfw.so`.

## O Engine decision

Use this pinned GLFW Android backend as the implementation reference because its native poll and swap paths are now identified. Adapt the platform backend and JNI boundary only after the LTW context ownership design is settled. Keep O Engine's bounded queue, digital-control tracking, and reset behavior; do not transplant the upstream fixed-buffer queue. No actual Minecraft GLFW event delivery or rendered frame is claimed by this audit.


## LWJGL 3 function-table clarification

The generated LWJGL 3.3.3 `org.lwjgl.glfw.GLFW` source was inspected at the `3.3.3` tag. Its static `Functions` table obtains C addresses with `apiGetFunctionAddress(GLFW, "glfw...")`; this is a C ABI/function-pointer contract, not a generated GLFW JNI-symbol contract. Accordingly, the pinned `lwjgl-3.3.3-required-symbols.txt` file is the relevant exported-symbol gate for this module. The ART-side Mojo JNI methods (Surface callbacks, input forwarding, cursor callbacks and clipboard) are a separate boundary.

This clarification does not prove that a Minecraft window is created or rendered. The remaining runtime gates include loading the correct LWJGL core native libraries, retained renderer native-loader interception, creating a GLFW window against the LTW-owned context, applying live Surface transitions, polling input on the GLFW owner thread, and presenting a real frame on a device.


## Legacy LWJGL 2 Java artifact selection

O Engine now pins the MojoLauncher LWJGL 2-on-GLFW fork separately from the LWJGL 3 runtime:

- Repository: `MojoLauncher/lwjgl2-glfw`
- Commit: `bf01b1b05c417b55cffc04d85dbc3a9cfa3b93df`
- Release: `vv8m`
- Published Java artifact: `lwjgl.jar`, SHA-256 `8258d85e165e291aaea9c3888af9da516fa8c51135288d108b26503eab7b97b9`
- Published Android native archives are pinned separately for arm64-v8a, armeabi-v7a, x86 and x86_64.

The dependency installer now recognizes the legacy `org.lwjgl.lwjgl:lwjgl:*` coordinate and downloads the pinned Mojo Java archive into the corresponding `2.9.4-mojo` library path. Launch classpath construction resolves that same path, so the upstream desktop LWJGL 2 JAR is not accidentally added for Android. Modern `org.lwjgl:lwjgl:3.x` libraries are unaffected.

This is only the Java-classpath half of the compatibility adapter. The ABI-specific native archive is not yet installed into the active game native directory, and the GL4ES plan remains blocked until the LWJGL 2 native loading and EGL/swap lifecycle are verified. Do not infer that a legacy game launches from the presence of the pinned Java JAR.


## O Engine integration update: per-version LWJGL 2 native payload

The dependency installer now also selects the exact Mojo native JAR for the device ABI, verifies the pinned archive size and SHA-256, extracts only the expected `liblwjgl.so` / `liblwjgl64.so` entry with a 32 MiB extraction cap, and checks the extracted ELF ABI before placing it in `versions/<version>/natives`. The per-version native directory is included in HotSpot's `java.library.path`, `org.lwjgl.librarypath`, and JNA search path alongside the shared Android GLFW directory.

This does **not** remove the LWJGL 2 launch blocker in `OEngineLwjglNativeResolver`: the actual Android JNI load contract and GL4ES/OpenGL bridge still need to be validated and connected. The native artifact is staged groundwork, not proof of a working legacy renderer.
