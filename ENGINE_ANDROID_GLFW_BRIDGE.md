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

## Primary touch-pointer handling

The Android adapter preserves each touch pointer ID in the native event payload. The GLFW adapter now selects the first pointer that becomes active as the desktop primary mouse pointer, tracks that pointer ID rather than assuming it is numeric zero, and ignores secondary fingers for mouse-button state. This prevents a secondary finger's UP event from releasing the primary finger's held left mouse button. When the primary pointer is released, the next DOWN may become primary. Touch IDs remain platform event data; they are not interpreted as GLFW mouse-button indices.

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


## Input translation work now present

The Android-side input translation layer now has a standalone GLFW key mapper:

- Android physical keyboard codes are translated into GLFW 3 key values; unknown keys are rejected instead of passing Android key numbers into GLFW.
- Modifier flags are translated to GLFW modifier bits.
- Android mouse button bit flags are translated to GLFW's zero-based button indices.
- Key repeat and Unicode codepoint metadata are represented in the platform-neutral event.
- Touch pointer IDs are retained; mouse wheel carries both axes; gamepad motion exposes six analog samples plus hat axes.
- Queue coalescing replaces a pending motion sample in its original FIFO position, so it does not move pointer motion across intervening key transitions.
- Queue overflow continues to request an explicit all-controls-up reset if a key/button transition cannot be queued.

This is Android-to-GLFW data preparation only. It is not connected to Pojav's native callback ABI or a live GLFW event loop. In particular, gamepad axis conventions still require device normalization and end-to-end tests against the actual native backend.

## Current status

The LTW-tracked EGL context and Android Surface attach/swap primitives exist. They are groundwork only. The native GLFW platform backend, event-loop consumer, input callbacks, actual Minecraft swap integration, and physical-device rendering verification are not yet implemented.

## Focus-loss and canceled-touch safety

A further platform-neutral safety layer now tracks held digital controls. It suppresses duplicate DOWN transitions, forwards REPEAT only for controls already held, ignores unmatched UP events, and can synthesize UP events in reverse press order when focus is lost or an input-device class disconnects. The Android adapter also preserves independent motion samples for each touch pointer, emits UP events for every active touch pointer on ACTION_CANCEL, and handles primary mouse ACTION_DOWN/ACTION_UP in addition to mouse button actions.

The tracker is now wired to the fullscreen game SurfaceView through an Android-side forwarder. Focus loss, surface destruction, and game-view dismissal synthesize releases; key, touch, mouse, hover, wheel, and gamepad motion events are translated and sent in bounded batches over the independent input Messenger endpoint into the Rust-native queue. The queue is still not consumed by GLFW, so this is end-to-end Android capture → IPC → native queue transport, not yet working in-game keyboard/mouse/gamepad input.


## Independent input IPC ingress foundation

The game process now exposes a second Messenger endpoint on a dedicated `OEngineInputIngress` HandlerThread. The health-check handshake returns this endpoint to the launcher process, and the client can send bounded batches of typed input events through it. The ingress validates array lengths, batch size, enum values, and each event's numeric invariants before passing the batch to the Rust-native bounded queue. The queue exposes the C ABI `oengine_input_drain()` for the future GLFW event-poll hook, tracks held digital controls, and converts transition overflow into an all-controls-up reset. The event queue is capped at 512 entries and held-control tracking at 1024 entries.

This split is deliberate: the original game-process command HandlerThread is also the HotSpot/JVM owner thread and can remain inside Minecraft's synchronous `main()` call. The new endpoint can still receive Binder messages without moving EGL or JNI calls to an unsafe thread.

**Not yet connected:** the SurfaceView does not send events through this channel, and no GLFW/game-thread consumer calls `oengine_input_drain()` yet. The endpoint currently establishes responsive transport and validation only; it does not produce in-game input. The next required step is to connect the queue to the exact native GLFW event-poll contract after verifying the packaged LWJGL/GLFW implementation. Surface attach/resize/detach also still needs to be consumed on the LTW EGL owner/game thread rather than the blocked command handler.


## Rust-native input queue and C handoff

The dedicated Messenger ingress now forwards validated batches into a process-global Rust queue in `liboengine_native.so`. This avoids a subtle but important two-VM mistake: the Android launcher code runs on ART, while Minecraft and its LWJGL native library run inside embedded HotSpot. A GLFW native function must not try to call an ART/Kotlin class using HotSpot's `JNIEnv`.

The queue instead exposes a plain C ABI in `native/oengine/include/oengine_input_bridge.h`:

- `oengine_input_drain(output, capacity)` — called by the GLFW event loop on the game thread
- `oengine_input_reset_pending()` — reports whether digital transition overflow requires reset
- `oengine_input_clear_session()` — clears a dead session

The Android JNI batch method writes into this same Rust queue. Rust host tests cover FIFO motion coalescing, bounded overflow, release recovery, unmatched/duplicate transitions, and ensuring reset does not discard releases when the consumer's output buffer is too small. The native queue is now the intended production handoff; the actual GLFW poll loop has not been wired to it yet. Bulk text events are currently rejected by the native batch API; per-key Unicode codepoint and modifier metadata are supported.


## SurfaceView-to-native input transport

The fullscreen game SurfaceView now requests keyboard focus and forwards Android key, touch, mouse-button, mouse-hover, scroll, and gamepad-motion events through `OEngineAndroidInputAdapter`. `OEngineGameInputForwarder` serializes held-control tracking and chunks messages to the Binder limit of 128 events. Focus loss, surface destruction, and view dismissal send synthesized key/button/touch releases. The isolated process validates each batch and writes it into the Rust queue.

This closes the Android frontend and process-transport gap. It does **not** yet make Minecraft respond: the Rust queue's `oengine_input_drain()` still needs to be called by the exact GLFW `pojavPumpEvents`/equivalent event-poll backend, and drained events must be delivered to the GLFW callback/state machinery. Android gamepad button keycodes also need a backend-specific mapping before claiming gamepad support.


## GLFW callback dispatch ABI

A native callback dispatcher now sits directly on the Rust queue. `oengine_input_dispatch(window, callbacks, capacity)` drains a bounded batch, releases the queue mutex, then invokes callback pointers on the calling thread. The callback table mirrors the standard GLFW callback signatures for key (including Android physical scancode), character, character-with-modifiers, cursor position, mouse button, and scroll. It explicitly converts O Engine DOWN/UP/REPEAT values (0/1/2) to GLFW PRESS/RELEASE/REPEAT values (1/0/2), rather than forwarding the internal enum unchanged. Touch and gamepad events are preserved through a separate raw platform-event callback instead of being misrepresented as mouse buttons. Overflow recovery releases are delivered through the same key/mouse callback paths.

The callback pointers must be obtained from the active embedded HotSpot GLFW implementation. Never pass ART method pointers or call Kotlin through HotSpot's `JNIEnv`. The dispatcher is now test-covered, Android APK CI checks that its C symbol is exported for every shipped ABI, and native host CI compiles the public C header with strict warnings. No packaged GLFW implementation calls it yet.

### Pojav reference contract inspected

The PojavLauncher source was inspected at its final pre-discontinuation commit `6eb830ba7aa8465d872a9ef0a8d87592c978e292` (repository branch `v3_openjdk`; project is discontinued). Its LWJGL 3.4.0 Java GLFW fork loads `libpojavexec.so`, whose function table includes `pojavCreateContext`, `pojavMakeCurrent`, `pojavSwapBuffers`, `pojavPumpEvents`, `pojavStartPumping`, and `pojavStopPumping`. Its `glfwPollEvents()` calls StartPumping, invokes PumpEvents once per window, then StopPumping. The native input bridge registers HotSpot callback function pointers through `nglfwSetKeyCallback`, `nglfwSetCharCallback`, `nglfwSetCursorPosCallback`, `nglfwSetMouseButtonCallback`, and `nglfwSetScrollCallback`.

This confirms the shape of the callback boundary that O Engine's new C table can support. It does **not** mean the Pojav Java fork or `libpojavexec.so` has been copied, packaged, or linked into O Engine. Pojav's library is a coordinated multi-file native module (EGL bridge, context bridge, input bridge, environment, JVM hooks, and more); taking only its input file would not create a working GLFW backend. The reference is used for ABI study and provenance, not as a claim that the backend is already present.


### Keyboard scancode ABI correction

The Android input model already carried Android's physical `scanCode`, but the JNI native entry point's parameter list had fallen out of sync with Kotlin: Kotlin passed the scancode array while Rust omitted it. This shifted the subsequent JNI array arguments. The JNI signature now accepts the scancode array in the exact Kotlin argument position, validates its length, copies it, and stores it in the C event structure. GLFW key dispatch now passes that physical scancode instead of hard-coded zero. The C structure appends the field to preserve all existing field offsets; ABI layout tests assert the new 48-byte structure and field offsets, and callback tests assert the scancode value.


## Physical keyboard fidelity

The Android key adapter now preserves `KeyEvent.scanCode` separately from the GLFW logical key value. That value crosses the Messenger arrays and JNI boundary into the native event struct, then reaches the GLFW key callback's scancode parameter. Unicode input is dispatched through both the regular character callback and the character-with-modifiers callback when those callbacks are registered. This avoids substituting the GLFW key value for the hardware scancode and retains modifier state for text callbacks.


### Retry-safe Android input transport

The launcher-side forwarder now retains failed Messenger batches and retries them before accepting newer events. This matters for key/button state: if a DOWN could not be sent, a later UP or a new key must not overtake it; if focus is lost while a batch is pending, the synthesized release is appended behind that DOWN and both are retried in order. Pending batches are bounded, and each batch stays within the 128-event Binder limit. Tests cover a failed DOWN, retry ordering, and a queued focus-loss release.

The native C event structure is explicitly 48 bytes with a reserved trailing word, keeping array stride stable across 32-bit and 64-bit Android ABIs. Its public C header test asserts every field offset and the full structure size. The callback table now has seven pointers, including both GLFW character callback variants.


### Atomic native batch admission

JNI input batches are now admitted transactionally. Rust stages the entire batch against a cloned bounded queue and publishes the staged queue only if every event is accepted. If any transition cannot fit, none of that batch is exposed to the game thread; the live queue instead requests an all-controls-up reset for already-held controls. This prevents a Binder batch containing a press/release sequence from being partially applied. The native unit test fills the queue with held controls, submits a two-event batch that cannot fit, and verifies that neither event leaks into the live queue and reset recovery is requested.


### Pinned Android GLFW poll-path adapter

O Launcher now builds the pinned MojoLauncher Android GLFW 3.5 fork and its MojoExec dependency as separate Android shared libraries for arm64-v8a, armeabi-v7a, x86, and x86_64. A reproducible source patch is applied in CI to the fork's Android poll/wait paths. On the GLFW event thread it drains O Engine's Rust queue and maps GLFW key, character, mouse, scroll, and primary-touch events into GLFW's own internal input callbacks. The Rust ingress also wakes GLFW's wait-event condition after accepting a batch, and the wait paths drain again after waking. The character adapter intentionally uses GLFW's single internal character entry point so both GLFW character callback variants are delivered once, not duplicated.

The native backend is packaged and CI checks its GLFW entry points and the O Engine wake symbol. This is an integration point in the pinned Android GLFW backend, **not proof that Minecraft's downloaded LWJGL native/Java pair is already using that backend**. The runtime classloader/native-library binding and the renderer's single EGL-context ownership still need to be connected before in-game input or rendering can be claimed. Touch pointer zero maps to the primary mouse; gamepad event adaptation remains pending.


### Private runtime staging and integrity checks

Before renderer/JVM setup, the isolated game process now stages the packaged Android GLFW dependencies into its private O Engine directory for the device ABI. It checks each ELF machine/ABI, rejects obvious non-Android ELF markers, copies atomically, verifies SHA-256, and writes `oengine-android-glfw-manifest.json`. The session log records the ABI, library hashes (short prefixes only), and manifest path. Unit tests cover successful staging, wrong-ABI rejection, and unsupported ABIs.

This makes the exact native backend artifacts available at stable private paths for the next integration phase. It does not load them into LWJGL or certify all transitive Android linker dependencies; the ELF inspector intentionally labels matching files as Android compatibility unverified. The `org.lwjgl.glfw` Java/JNI provider bridge is still a separate required task.


### LWJGL runtime pairing gate

The launch planner now inspects the actual Minecraft classpath for the matching `lwjgl-glfw` module and its `org.lwjgl.glfw.GLFW` class. For LWJGL 3, it explicitly blocks launch while the Android platform library is not wired to that class's LWJGL JNI/function-provider contract. This avoids treating the presence of a separately named `libglfw.so` as proof that Minecraft can call it. Tests cover both a missing GLFW module and the currently unadapted upstream module.


### Exact LWJGL 3.3.3 native export contract

Inspected the generated upstream LWJGL 3.3.3 `org.lwjgl.glfw.GLFW` binding. It resolves 117 unique C functions from the shared library using `apiGetFunctionAddress`; the configurable property is `org.lwjgl.glfw.libname`, and LWJGL explicitly permits an absolute library path. O Engine now pins the complete 117-symbol list in `native/android-glfw/lwjgl-3.3.3-required-symbols.txt`. Android CI compares every packaged ABI's dynamic exported symbol table against that list, in addition to checking Mojo's Android JNI entry points and the O Engine wake hook.

This verifies the native symbol surface needed by the selected LWJGL Java binding, rather than just checking that a file named `libglfw.so` exists. It does **not** yet establish that every API has compatible runtime semantics, nor does it solve Android Surface-provider initialization or the existing LTW/GLFW EGL-context ownership decision. The absolute-path `org.lwjgl.glfw.libname` setting remains gated until those runtime pieces are connected.
