# O Launcher Engine

O Launcher is an original Android Minecraft Java launcher. Its engine is developed in this repository; it is not a fork or rebranded copy of another launcher.

## Engineering principles

- Kotlin-first Android orchestration, with native code introduced only where profiling and platform requirements justify it.
- Explicit subsystem boundaries and typed contracts; UI code does not own installation or process logic.
- Structured concurrency for network and filesystem work, bounded parallelism, cancellation, and deterministic cleanup.
- Verify downloaded artifacts before promotion into the active game directory.
- Treat archives and metadata as untrusted input; validate paths, hashes, sizes, and version compatibility.
- Keep runtime discovery and JVM process configuration in O Engine, not in Compose screens.
- Prefer Android public APIs and supported NDK interfaces. Do not depend on private platform symbols.
- No copying or vendoring another launcher's engine. External projects and platform documentation may be studied as references; implementation and architecture remain ours.

## Target subsystem boundaries

| Subsystem | Responsibility |
| --- | --- |
| Engine Core | Engine lifecycle, typed state, paths, diagnostics, and subsystem coordination |
| Version & Install | Mojang metadata, version inheritance, libraries, assets, client files, integrity checks |
| Runtime | Android Java runtime acquisition, safe extraction, registration, discovery, compatibility checks |
| Process | JVM argument assembly, environment, process lifecycle, stdout/stderr, termination |
| Graphics | Android graphics backend and the compatibility surface required by Minecraft's LWJGL calls |
| Input & Window | Landscape game surface, keyboard, pointer, controller, focus, and resize events |
| Accounts | Microsoft authentication, offline identity, token lifecycle, session properties |
| Profiles | Per-profile version, loader, memory, resolution, runtime, and game-directory settings |

## Current implementation status

- The Android app and UI are Kotlin + Jetpack Compose.
- Minecraft version metadata, client JAR, compatible library artifacts, assets, and launch-plan preparation exist.
- Android Java runtime archive download and guarded TAR/XZ extraction exist.
- OLauncherEngine currently owns runtime paths, runtime discovery, a real java -version subprocess probe, and a guarded launch-command preview.
- OEngineGraphics can create a temporary Android EGL pbuffer context, probe OpenGL ES 3 then ES 2, collect driver identity/extensions, and release EGL resources.
- OEngineEglSession provides a thread-confined Android EGL window-surface/context lifecycle with make-current, swap-interval, buffer-swap, and deterministic teardown operations.
- The EGL window session is an Android OpenGL ES foundation, not desktop OpenGL; it does not provide GLFW/LWJGL compatibility. The graphics compatibility layer and actual Minecraft process launch are not complete.
- A successful CI build proves compilation, not that a downloaded runtime starts on a physical phone. Device verification is required.

## Next engineering gates

1. Pass CI for the engine runtime discovery and Java process probe.
2. Run the extracted JVM on physical Android devices and capture its diagnostics.
3. Build a validated JVM command from the launch plan, including classpath, native paths, memory, and game arguments.
4. Validate EGL/OpenGL ES probe and window-surface lifecycle on physical Android devices.
5. Specify the exact desktop-GL/GLFW/LWJGL API compatibility contract needed by the selected Minecraft versions; map each API to Android GLES or a justified native implementation.
6. Implement and test that compatibility contract incrementally; do not assume GLES is desktop OpenGL.
7. Connect process lifecycle, input/window handling, and crash diagnostics.
8. Launch a clean vanilla version before adding mod-loader installation.

## Quality gates

Every subsystem should have focused tests for parsing, path safety, metadata edge cases, cancellation, and failure recovery. Device-dependent behavior must be labeled as unverified until tested on actual hardware. Avoid claims of playable Minecraft until a clean vanilla launch has been demonstrated.