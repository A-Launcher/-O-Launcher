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
- OEngineGraphicsBackend is the engine-facing graphics contract; OEngineGraphicsCompatibilityGate explicitly rejects treating an OpenGL ES context as desktop OpenGL. GLES extension enumeration uses the API appropriate to ES 2 versus ES 3.
- OEngineRendererArtifactGate validates renderer artifact metadata, private-directory containment, ELF class, and ELF machine against the declared Android ABI before a future adapter can load it. It never loads native code.\n- OEngineGraphicsStack tracks the latest upstream targets: LTW at commit `3f1f4df6c80c925ea34067b1d943a84acce0184d` (the upstream repository has no published release tag in the inspected page), MobileGlues 2.0.0, and LWJGL 3.4.3 as a reference. LTW and MobileGlues are alternative renderer providers, not layers to load simultaneously. The preflight inspects game-pinned LWJGL modules and applies MobileGlues' documented GLES 3.0 minimum (3.2 preferred). Neither renderer is installed, loaded, or bundled yet. The native artifact gate is ready for verified artifacts, but no upstream provider loader contract has been activated.
- The EGL window session is an Android OpenGL ES foundation, not desktop OpenGL; it does not provide GLFW/LWJGL compatibility. The graphics compatibility layer and actual Minecraft process launch are not complete.
- A successful CI build proves compilation, not that a downloaded runtime starts on a physical phone. Device verification is required.

## Next engineering gates

1. Pass CI for the engine runtime discovery and Java process probe.
2. Run the extracted JVM on physical Android devices and capture its diagnostics.
3. Build a validated JVM command from the launch plan, including classpath, native paths, memory, and game arguments.
4. Validate EGL/OpenGL ES probe and window-surface lifecycle on physical Android devices.
5. Inventory the exact desktop-GL/GLFW/LWJGL calls used by each supported Minecraft version; preserve the LWJGL versions pinned by Mojang metadata rather than forcing the newest release.
6. Add selectable renderer-provider adapters for LTW and MobileGlues; activate only one provider per game session.
7. Integrate the current LTW source and MobileGlues 2.0.0 through verified, supported Android loader contracts. Respect LTW's LGPL-3.0 and MobileGlues' LGPL-2.1 obligations before redistributing components; do not treat either project's source repository or MobileGlues plugin APK as a drop-in native library.
8. Implement and test that compatibility contract incrementally; do not assume GLES is desktop OpenGL.
9. Connect process lifecycle, input/window handling, and crash diagnostics.
10. Launch a clean vanilla version before adding mod-loader installation.

## Quality gates

Every subsystem should have focused tests for parsing, path safety, metadata edge cases, cancellation, and failure recovery. Device-dependent behavior must be labeled as unverified until tested on actual hardware. Avoid claims of playable Minecraft until a clean vanilla launch has been demonstrated.

## Reproducible LTW artifact build

The dedicated `.github/workflows/ltw-renderer.yml` workflow checks out the exact upstream LTW revision `3f1f4df6c80c925ea34067b1d943a84acce0184d`, builds its release AAR with the upstream Android Gradle module, and validates every packaged `jni/<abi>/libltw.so` ELF header against its declared ABI before uploading the AAR and checksum as a CI artifact. The workflow deliberately builds LTW outside the O Launcher app source tree: it does not fork or copy LTW source into O Launcher.

This is an artifact-production and integrity gate, not runtime integration. O Engine must still validate the downloaded artifact on-device, establish the required EGL/native-library load order and environment, and test real Minecraft versions before bundling or loading LTW in a game session. LTW's native initialization dynamically opens EGL and resolves GLES entry points during library initialization, so merely copying `libltw.so` into the APK would not constitute a working renderer integration.


## LTW integration boundary discovered from pinned upstream source

The pinned LTW implementation is not a renderer that can be initialized by simply loading `libltw.so` after O Engine creates an EGL context. Its native constructor opens `libEGL.so` (or the `LIBGL_EGL` override), resolves the host `eglGetProcAddress`, and initializes GLES entry points. LTW exports wrappers for `eglCreateContext`, `eglDestroyContext`, `eglMakeCurrent`, and `eglGetProcAddress`. The wrappers maintain LTW's own per-context state: `eglCreateContext` registers each host context, and `eglMakeCurrent` initializes and activates the matching LTW context state.

Consequently, the game-side EGL calls must pass through LTW's exported EGL wrappers for the lifetime of each game context. A context created directly by Android's Java `EGL14` API is not automatically registered in LTW's context map. O Engine's existing `OEngineEglSession` remains a native GLES capability/window-session foundation; it must not be mistaken for an LTW-managed Minecraft context.

Before implementation, the launch architecture must establish a verified symbol-resolution/interposition route for the Minecraft LWJGL EGL calls (and the correct EGL implementation path), then prove create/make-current/destroy all traverse LTW wrappers on the game render thread. The current `ProcessBuilder` Java smoke-test path is not proof of this graphics process contract. No `System.load`, `LD_PRELOAD`, or EGL override should be added speculatively: Android linker behavior, process boundaries, and LWJGL's actual Android native EGL loading path must be verified first.


## LTW and LWJGL EGL resolution contract

Inspection of the pinned LTW source confirms the concrete integration path that O Engine must reproduce: LTW's native constructor opens `LIBGL_EGL` when set (otherwise `libEGL.so`), resolves the host `eglGetProcAddress`, then initializes its own EGL context map and GLES function table. LTW exports `eglGetProcAddress`, `eglCreateContext`, `eglDestroyContext`, and `eglMakeCurrent`; its `eglGetProcAddress` routes the three context functions through LTW and redirects other EGL/GLES calls to the host driver or LTW overrides. It also exports `glXGetProcAddress` specifically for automatic discovery by LWJGL's symbol resolver. The pinned version script exports `gl*` and `egl*` symbols.

LWJGL's EGL binding supports a custom `FunctionProvider` and a configurable EGL library name; its GLES binding resolves GLES functions through the EGL context provider's `eglGetProcAddress`. These are promising supported integration seams, but O Engine has not yet proved that the exact Minecraft-version LWJGL native binaries and Android process loader resolve LTW's symbols as intended. The launch implementation must set all LWJGL configuration before the EGL/GLES classes initialize, load the exact ABI's LTW library in the game process, and test context creation/current/destroy plus GL function resolution. The launcher must not call the host EGL context API directly for the game context if it expects LTW's context map to track that context.

A new JVM unit-test suite covers the graphics preflight's version policy, MobileGlues-only GLES floor, provider availability gate, and exclusive renderer selection. These tests validate O Engine's decisions, not native graphics behavior.


## MobileGlues 2.0.0 host-launcher contract

The official MobileGlues 2.0.0 plugin source shows that the downloadable APK is a separate Android plugin application, not a native renderer library by itself. Its manifest advertises an FCL plugin through `fclPlugin=true` and renderer metadata (`MobileGlues:libmobileglues.so:libmobileglues.so`), plus launcher environment metadata. The published Pojav environment sets `LIBGL_ES=3`, `POJAV_RENDERER=opengles3`, `POJAVEXEC_EGL=libmobileglues.so`, and `LIBGL_EGL=libmobileglues.so`. Its README documents host OpenGL ES 3.x, minimum 3.0 and best 3.2.

The plugin's AIDL service is for renderer information and benchmarking; it is not a generic game-renderer loading API. The plugin's own code discovers ANGLE from a known list of launcher packages and explicitly checks native-library directory accessibility and linker namespace constraints. O Launcher is not in that published list. Therefore MobileGlues integration requires a verified O Launcher package/renderer-discovery contract and an on-device test proving that O Launcher's process can load the selected plugin library and its dependencies. We must not assume that another app's `nativeLibraryDir` can be loaded across Android linker namespaces.

Do not bundle the plugin APK as if it were `libmobileglues.so`, and do not mark MobileGlues available merely because the plugin package is installed. Keep the provider unavailable until the actual native library path, ABI, dependencies, user-selected driver/ANGLE configuration, and game-process EGL lifecycle all pass validation.


## First reproducible LTW artifact result

LTW upstream commit `3f1f4df6c80c925ea34067b1d943a84acce0184d` built successfully as a release AAR in Actions run #7. The resulting AAR SHA-256 is `76ea49bdd54664955e67aafd7dca78919909e4411493a2c9f5865c23d8a54790`. It contains `libltw.so` for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; all four ELF class/machine pairs match their ABI directories.

Independent inspection of all four libraries confirmed exports for `eglGetProcAddress`, `eglCreateContext`, `eglDestroyContext`, `eglMakeCurrent`, and `glXGetProcAddress`. Their direct ELF dependencies are limited to Android system libraries `liblog.so`, `libm.so`, `libdl.so`, and `libc.so`. The dedicated LTW workflow now repeats these checks using the Android NDK's `llvm-nm` and `llvm-readelf` before publishing its artifact.

This validates artifact identity, ABI, required exported symbols, and direct dependencies. It does not prove that Android's linker namespace, the game's exact LWJGL build, or Minecraft's render thread will resolve and use the wrappers correctly. Runtime integration and device tests remain mandatory.


## MojoLauncher renderer and JVM-launch reference (inspected 2026-11-06)

O Engine inspected the public MojoLauncher `v3_openjdk` branch at commit `1b8d90424c1cb791321d45f60ac62c54f4b013fa` and its `mojoexec` submodule at `9ec4b836e93900135bad823eaac16ebc81cf6b98`. These are implementation references only; O Engine does not import or copy their launcher code.

The reference establishes a concrete LTW startup sequence:

1. Renderer compatibility checks require GLES 3+ and the selected renderer library to exist.
2. The renderer selects a host GLES provider (system driver or configured ANGLE) and supplies `LIBGL_EGL` and `LIBGL_GLES` environment values when explicit paths are needed.
3. Before game startup, the native EGL preparation routine opens the selected renderer library and records the requested GLES context major version.
4. During JVM native-library loading, a native hook is installed for LWJGL's `org.lwjgl.system.linux.DynamicLinkLoader.ndlopen(long,int)`. The reference implementation redirects its patched LWJGL request for `libGLMojo.so` to the already-prepared renderer EGL handle.
5. The launcher creates the Android Java runtime in-process with `JNI_CreateJavaVM`, rather than starting the game's JVM through an ordinary child-process command.

The hook target `libGLMojo.so` and the `DynamicLinkLoader.ndlopen` native method are part of MojoLauncher's specific LWJGL integration. They are not assumed to exist in Mojang's unmodified, metadata-pinned LWJGL artifacts. O Engine must own and validate its own Android LWJGL native-loading adapter for each supported LWJGL generation before enabling this renderer path.

### O Engine implementation boundary

O Engine currently has a Java executable smoke test using `ProcessBuilder`; it does not yet create a game JVM in-process. That smoke test cannot install a native hook inside a separate Java process and must not be reused as the Minecraft game-launch mechanism.

A new `OEngineRendererLaunchPlanner` now prepares LTW's pre-JVM configuration: validated LTW artifact and ABI, host GLES version, optional explicit EGL/GLES library paths, `LIBGL_EGL`/`LIBGL_GLES` values, preloading requirement, and explicit blockers until the LWJGL resolver and any required linker-namespace access are verified. It performs no native loads and changes no process environment. JVM unit tests cover missing resolver hooks, GLES 2 rejection, GLES 3.2 readiness, GLES 3.1 warning, explicit driver environment mapping, and external-driver namespace gating.

Next implementation gate: design O Engine's own in-process JVM bootstrap and Android LWJGL native-loader adapter, then prove that the actual Minecraft LWJGL artifacts for each selected version route EGL acquisition through the prepared LTW handle. Do not hardcode MojoLauncher's patched symbol/library names into O Engine or enable LTW until this is demonstrated.


## In-process JVM bootstrap planning

O Engine now has `OEngineJvmBootstrapPlanner`, which prepares the arguments and runtime paths for a future JNI Invocation API bridge. It locates `libjvm.so` in known Android JRE layouts, validates the runtime executable/release metadata, classpath files, native directory, working directory and main class, and normalizes launcher-owned JVM properties without discarding user heap or other JVM flags. The resulting plan keeps VM options and Minecraft application arguments separate, matching the JNI_CreateJavaVM boundary.

The launch preview now includes this structural JVM plan when a matching runtime is installed. This is deliberately not a VM launch: the native bridge must still resolve `JNI_CreateJavaVM`, validate the JRE's native dependencies and Android linker visibility, and prove that the selected OpenJDK VM can coexist with the app's Android Runtime. The preview continues to block game launch until that native bootstrap and LWJGL/EGL hooks are implemented.


## Native JVM Invocation API diagnostic bridge

The app now builds an original NDK/CMake library, `liboengine_bootstrap.so`, for arm64-v8a, armeabi-v7a, x86 and x86_64. Its Kotlin bridge is lazy: it is not loaded during normal launcher startup. When explicitly called with a structurally valid JVM plan, the native diagnostic opens that plan's `libjvm.so`, checks that both `JNI_CreateJavaVM` and `JNI_GetCreatedJavaVMs` are exported, closes the handle, and returns a structured status. It does not create a VM or execute Minecraft.

Android CI now inspects the built APK and verifies that the native probe library and its JNI entry point exist for all four ABIs. This verifies packaging and symbol export only. A device-side probe is still required to establish whether each downloaded Android JRE can actually be opened from O Launcher's linker namespace; successful symbol discovery is not a green light to create the VM. The next native milestone is a guarded, one-shot VM creation path with version-specific class-loader/native-library hooks and an explicit teardown/failure model.
