# O Engine Android GLFW platform bridge

## Research reviewed

This design checkpoint is based on direct source inspection of:

- PojavLauncher `v3_openjdk`: `app_pojavlauncher/src/main/jni/egl_bridge.c`, `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java`, and `app_pojavlauncher/src/main/java/org/lwjgl/glfw/CallbackBridge.java`.
- MojoLauncher `v3_openjdk`: `app_mojo/src/main/java/git/artdeell/mojo/game/platform/Platform.java`, `platform/backend/PlatformBackend.java`, `platform/backend/GLFWBackend.java`, `render/SurfaceProvider.java`, and `render/SurfaceViewSurfaceProvider.java`.
- LTW pinned at `3f1f4df6c80c925ea34067b1d943a84acce0184d`: `ltw/src/main/tinywrapper/egl.c` and `egl.h`.

No upstream source files are copied by this document. Preserve upstream license notices if any implementation is later ported.

## Findings that affect O Engine

### Pojav

Pojav treats the Android window, EGL context, GLFW event injection, and swap path as one platform integration:

- `ANativeWindow_fromSurface` acquires the Android native window; the bridge explicitly acquires/releases native references.
- Renderer-specific bridge setup initializes the context and window from the Android native window.
- `pojavMakeCurrent`, `pojavSwapBuffers`, and `pojavSwapInterval` delegate to the active renderer bridge.
- `CallbackBridge` exposes keyboard, character, mouse, scroll, and window-size event entry points. Its native side feeds the GLFW implementation; Java-side event methods alone do not implement GLFW input.

### Mojo

Mojo separates Android view lifecycle from the selected platform backend:

- `SurfaceProvider` reports available, resized, and destroyed as distinct callbacks.
- `GLFWBackend` forwards surface-created/updated/destroyed events to the native GLFW implementation.
- The same backend forwards mouse position/buttons, keyboard and Unicode text, scroll, visibility, and hover state.
- `Platform` keeps pending Surface state so a backend initialized later can receive the current window.

This separation is useful for O Engine, but the backend is not merely a Kotlin event adapter: it depends on a compatible native GLFW implementation.

### O Engine's current thread constraint

- The service creates its EGL session on the `OEngineGameProcess` HandlerThread.
- EGL session methods verify that calls happen on the creating thread.
- The service currently invokes Minecraft's main method synchronously on that same HandlerThread.
- Once Minecraft's main loop is running, that HandlerThread is occupied. Its Messenger handler cannot process later Surface-resize/detach messages until the call returns.
- Moving EGL calls to an arbitrary IPC or UI thread would violate the current EGL session's thread-ownership contract and can break the current context.

Therefore a Messenger input event handler or a second Kotlin thread calling EGL is not a valid runtime solution.

## Required architecture

The next implementation must establish one explicit game-thread platform event boundary:

1. **Android frontend (launcher process):** a Surface provider reports create/resize/destroy; input adapters translate Android key, motion, text, and gamepad events into bounded, typed platform events.
2. **IPC ingress (game process):** Binder/Messenger ingress must remain responsive while Minecraft main is executing. It validates event type, payload size, sequence, and session generation, then writes into a bounded thread-safe queue. It must not call EGL from the Binder thread.
3. **Game-thread consumer:** the actual LWJGL/GLFW event loop consumes queued platform events on the correct thread. Surface replacement and EGL make-current/destroy operations happen here, not on the blocked service command handler.
4. **Native GLFW adapter:** implements the native GLFW window/surface hooks and callback delivery expected by LWJGL. It must own window dimensions, visibility, cursor/grab state, key/button state, text input, scroll, and event polling.
5. **Presentation:** the game's real swap path must call the active window's swap implementation. A separately callable `eglSwapBuffers` helper does not wire Minecraft's GLFW swap loop.
6. **Lifecycle:** pause/resume, Surface loss/recreation, orientation changes, and game exit must be serialized through the same game-thread boundary. Pending input must be discarded or reset on a new Surface generation; pressed keys/buttons must be released on focus loss.

## Non-negotiable correctness constraints

- Do not claim input works merely because Android events are captured or sent over IPC.
- Do not call LTW EGL session methods from the main/UI thread or an unrelated IPC thread.
- Do not create a second EGL context alongside the LTW-tracked context.
- Do not treat a raw ANativeWindow handle as a GLFW window.
- Do not report a rendered frame until the actual Minecraft/LWJGL swap path presents one.
- Do not assume Pojav's `CallbackBridge` Java class is sufficient without its matching native GLFW implementation.
- Keep queue capacity bounded; coalesce high-frequency pointer motion/resize events, but never silently drop key/button release events. On overflow, reset input state or fail the session visibly rather than leaving stuck keys.

## Implementation gates

### Gate A — prove the native GLFW contract

Identify the exact GLFW implementation and LWJGL version shipped in the O Engine runtime, its exported symbols, the window creation path, event-poll entry point, swap path, and callback ABI. Pin the upstream source revision and document license/provenance. Do not implement against guessed symbols.

### Gate B — implement the platform backend

Implement native window creation/destruction, Surface generation handling, size/visibility/focus callbacks, and swap on the existing LTW context. Verify all JNI/native symbols in CI for each shipped ABI.

### Gate C — implement input

Add a bounded IPC event protocol and a game-thread consumer. Port/adapt the native callback queue only after Gate A confirms the exact GLFW ABI. Cover Android key mapping, Unicode text, mouse buttons/relative motion, wheel, touch, gamepad axes/buttons, and focus-loss release.

### Gate D — end-to-end verification

Run instrumented tests on a physical Android device: attach, render, resize, detach, recreate, pause/resume, input, and close. Confirm an actual Minecraft frame reaches the Surface and the session log records lifecycle transitions.

## Current status

The LTW-tracked EGL context and Android Surface attach/swap primitives exist. They are groundwork only. The native GLFW platform backend, event-loop consumer, input callbacks, actual Minecraft swap integration, and physical-device rendering verification are not yet implemented.