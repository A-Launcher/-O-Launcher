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
- O Engine remains an original launcher and owns its architecture, APIs, lifecycle, and UI. Selectively integrate technically necessary upstream components when that is safer or faster than reimplementing them; pin exact revisions, keep separable library boundaries, preserve licenses/notices and corresponding source, and document every adaptation. Do not fork/rebrand an entire launcher or copy its app architecture.

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
- OLauncherEngine owns runtime paths, runtime discovery, a real java -version subprocess probe, and a guarded launch-command preview. It also prepares a typed in-process JVM bootstrap plan; the isolated :game service now preloads LTW, creates HotSpot through JNI_CreateJavaVM, installs the native-loader hook, and dispatches the authenticated Minecraft main class on the VM-creating thread. This path is implemented but remains device-unverified.
- The Rust native JVM bridge is packaged in the Android APK for all four Android ABIs. It retains one validated `libjvm.so` handle, creates one HotSpot VM in the isolated :game process through JNI_CreateJavaVM, verifies core Java classes and a Java smoke-test main, installs version-specific native-library hooks, and invokes Minecraft main on the same VM-creating HandlerThread. ART/OpenJDK coexistence, real runtime compatibility, and a successful Minecraft title-screen launch remain device-unverified.
- OEngineGraphics can create a temporary Android EGL pbuffer context, probe OpenGL ES 3 then ES 2, collect driver identity/extensions, and release EGL resources.
- OEngineEglSession provides a thread-confined Android EGL window-surface/context lifecycle with make-current, swap-interval, buffer-swap, and deterministic teardown operations.
- OEngineGraphicsBackend is the engine-facing graphics contract; OEngineGraphicsCompatibilityGate explicitly rejects treating an OpenGL ES context as desktop OpenGL. GLES extension enumeration uses the API appropriate to ES 2 versus ES 3.
- OEngineRendererArtifactGate validates renderer artifact metadata, private-directory containment, ELF class, and ELF machine against the declared Android ABI before a future adapter can load it. It never loads native code.\n- OEngineGraphicsStack tracks the latest upstream targets: LTW at commit `3f1f4df6c80c925ea34067b1d943a84acce0184d` (the upstream repository has no published release tag in the inspected page), MobileGlues 2.0.0, and LWJGL 3.4.3 as a reference. LTW and MobileGlues are alternative renderer providers, not layers to load simultaneously. The preflight inspects game-pinned LWJGL modules and applies MobileGlues' documented GLES 3.0 minimum (3.2 preferred). Neither renderer is installed, loaded, or bundled yet. The native artifact gate is ready for verified artifacts, but no upstream provider loader contract has been activated.
- The EGL window session is an Android OpenGL ES foundation, not desktop OpenGL; it does not provide GLFW/LWJGL compatibility. The in-process JVM and authenticated Minecraft main-dispatch pipeline are implemented. The graphics compatibility layer is not yet proven against Minecraft's exact LWJGL/EGL calls, so a playable launch is not yet verified.
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

## JVM bootstrap design

The design in [`JVM_BOOTSTRAP_DESIGN.md`](JVM_BOOTSTRAP_DESIGN.md) remains the reference for isolated game-process ownership, Android linker preparation, VM creation/teardown, cross-VM JNI rules, and device acceptance. The implementation has advanced beyond the original diagnostic-only stage: the native bridge now creates HotSpot and invokes Minecraft main. Physical-device validation, renderer interoperability, graceful VM shutdown, and full crash recovery remain open gates.

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

The app builds and packages the original Rust `cdylib`, `liboengine_native.so`, for arm64-v8a, armeabi-v7a, x86 and x86_64. Its Kotlin bridge is lazy and is loaded only when the game process needs it. The bridge validates and retains the selected `libjvm.so`, creates a single HotSpot VM with `JNI_CreateJavaVM`, verifies Java core classes and a smoke-test class, and keeps the VM-creating thread attached for the session. The same thread invokes the selected Minecraft main class with the authenticated argument array. Main-class lookup and Java exceptions now return bounded stack-trace diagnostics; nested Java binary class names are accepted.

Android CI cross-compiles the Rust crate before Gradle, packages the four ABI libraries from `app/src/main/jniLibs`, and inspects the APK for both the JVM probe and LWJGL hook JNI exports. CI verifies packaging and symbol export, while runtime compatibility still requires a physical-device test. The one-shot VM path and version-specific native-library hook are implemented; graceful VM teardown and ART/OpenJDK coexistence remain explicit failure/lifecycle work.


## Version-aware LWJGL native resolver

O Engine now inspects the exact Minecraft classpath before renderer startup. The resolver extracts Maven-layout LWJGL module names and versions, distinguishes LWJGL 2 from LWJGL 3, detects mixed major generations and conflicting module versions, and inspects the LWJGL 3 core JAR for the Linux `DynamicLinkLoader` class without loading game classes or executing native code.

The resolver deliberately treats Mojo's `ndlopen` marker as a warning that a patched interface may be present—not as permission to reuse it. Upstream LWJGL 3, LWJGL 2, unknown generations, missing core JARs, and Mojo-patched markers all remain blocked until O Engine has a separately verified Android interception adapter for that exact interface. The command preview now reports these version-specific blockers instead of using one generic LWJGL warning.

This is metadata/JAR inspection and unit-tested decision logic only. It does not install a Java instrumentation hook, intercept `dlopen`, preload LTW, or launch Minecraft. The next gate is to inspect upstream LWJGL's public EGL/FunctionProvider seams for each supported metadata version and implement an O Engine-owned adapter that routes game EGL context lifecycle calls through LTW.



## Native implementation language decision

O Engine will use Rust as the preferred language for new native engine subsystems: JVM bootstrap coordination, renderer/provider loading, EGL symbol routing, native diagnostics, and process-level safety checks. Kotlin remains responsible for Android UI, lifecycle, storage, and platform APIs. The existing small C++ JNI diagnostic is a temporary bridge while the Rust Android build pipeline and ABI packaging are established; it is not the intended language for expanding the native engine.

The Rust integration must build reproducibly for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`, expose a narrow C ABI to Kotlin/JNI, and include host-side unit tests plus Android ABI/link checks. No native component will create the game JVM or load a renderer until the selected JRE, LWJGL adapter, renderer artifact, and Android linker visibility have all passed explicit preflight gates.



### Upstream LWJGL native hook verification correction

Inspection of the official upstream LWJGL 3.3.1 and 3.3.3 generated `DynamicLinkLoader.java` confirms that `ndlopen(long,int)` with JNI descriptor `(JI)J` is already a standard upstream native method. MojoLauncher registers a replacement implementation for that method using JNI `RegisterNatives`; the method name itself is not a Mojo-only patch marker. O Engine's resolver therefore treats this signature as an upstream interception candidate, not as proof of a modified Mojo LWJGL build. The O Engine adapter remains blocked until it is implemented and verified against the actual selected class and JVM class loader.



## Rust native core bootstrap

The repository now has an independent Rust `cdylib` crate under `native/oengine` and a dedicated CI workflow. Host tests exercise exact renderer-alias matching and ensure unrelated native libraries are not redirected. Android CI cross-builds `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64` and validates that each ELF artifact exists.

The Rust crate now contains an O Engine-owned JNI registration entry point for the upstream LWJGL 3 `DynamicLinkLoader.ndlopen(long,int)` method. It registers against the exact `Class` supplied by the caller's game class loader, redirects only an exact configured renderer alias to a process-owned renderer handle, and delegates all other requests to the normal `dlopen` path. It refuses to replace a different active renderer configuration. The Rust library is now built for all four Android ABIs and packaged into the APK. However, the LWJGL hook is not yet activated: no renderer handle is prepared by Kotlin, and no Android device has validated the JNI/class-loader/linker behavior. The former C++/CMake JVM diagnostic is no longer part of the Android build; its Rust equivalent is now the APK's native bridge. The old C++ source files remain in the repository temporarily for reference and cleanup.



## Version-aware Java runtime policy

O Engine now resolves the Java target for each Minecraft version using Mojang's `javaVersion.majorVersion` when present, with a legacy version-range fallback for older metadata. The policy includes the supplied eras: Pre-Classic through 1.5.2 (Java 5), 1.6.1–1.11.2 (Java 6), 1.12–1.16.5 (Java 8), 1.17–1.17.1 (Java 16), 1.18–1.20.4 (Java 17), 1.20.5–1.21.11 (Java 21), and 26.1+ (Java 25).

Historical bytecode target and installable Android runtime are distinct. Since the current Android runtime source does not publish Java 5/6/16 builds, O Engine maps Java 5/6 targets to Java 8 and Java 16 targets to Java 17, both newer compatible runtimes. Modern targets 8/17/21/25 map directly. Unknown metadata targets are preserved and will fail with an explicit unavailable-runtime message rather than silently selecting a different Java major.

The official version list now includes Mojang release and snapshot entries, so 26.x snapshots are selectable. Metadata remains authoritative for snapshot runtime selection. The UI reports both the game's Java target and the actual Android runtime major selected.


## MojoLauncher engineering reference and licensing boundary

MojoLauncher `v3_openjdk` was inspected as an implementation reference for multi-runtime discovery/selection, in-process JVM loading, Java-version-specific native-library hooks (Java 8, 17 and 21 signatures differ), renderer setup, host EGL/GLES environment preparation, LWJGL native-load interception, and legacy-version renderer compatibility decisions. O Engine will implement its own Kotlin/Rust modules and tests around those concepts rather than wholesale-importing Mojo's launcher implementation. The inspected MojoLauncher repository is LGPL-3.0; any future source reuse or binary distribution must preserve applicable license notices and source-offer obligations. LTW and MobileGlues retain their separate upstream licenses.


## Instance isolation and automatic Android runtime selection

O Engine now has a named instance manager. The existing Default instance retains the legacy `filesDir/minecraft` path, while additional instances receive isolated `instances/<id>/game` roots for versions, libraries, assets, saves, and configuration. The Installations screen can create and switch instances; install and launch preparation operations use the selected instance root.

The Installations screen now offers a one-click install-and-prepare action: it verifies/reuses or downloads the selected client JAR, installs the metadata-selected libraries and assets, builds the launch plan, and automatically reuses or downloads/extracts the required Android OpenJDK runtime. Separate download actions remain available for troubleshooting and staged installs.

Mojang native classifier archives are resolved by device architecture and downloaded with metadata size/SHA-1 verification. They are retained in the libraries cache for inspection. They are not yet extracted or treated as Android-ready: Mojang commonly supplies Linux/glibc natives, which are not automatically compatible with Android/Bionic. O Engine must supply or adapt Android-native equivalents and validate ELF dependencies before launch.


## Android platform support requirements

Android does not provide a desktop Minecraft Java runtime or desktop OpenGL environment. O Launcher must supply and coordinate the compatibility components rather than assuming they exist on the device.

### Java runtimes
Bundle or securely download Android-built OpenJDK runtimes and select the runtime from each version's Mojang metadata, with a documented legacy fallback. The current downloadable runtime set is Java 8, 17, 21 and 25. Java 5, 6 and 16 are historical game targets and currently map to newer runtime packages where compatibility permits. Runtime packages must be architecture-specific, integrity-checked, isolated per instance, and validated before use.

### Graphics provider architecture
Keep graphics providers modular and mutually exclusive per game session:
- GL4ES: an OpenGL-to-OpenGL ES compatibility option, subject to the exact Minecraft/LWJGL version and feature requirements.
- LTW: the pinned OpenGL core-to-GLES translation provider; its native artifact is reproducibly built and ELF-validated, but game EGL context interception and runtime loading remain unintegrated.
- Mesa-based providers: optional software/hardware driver stacks, requiring explicit Android ABI, Vulkan/EGL, licensing, packaging and device compatibility validation.
- MobileGlues: optional GLES renderer provider, with a separate plugin/native loading contract; do not assume its plugin APK makes its native library accessible to O Launcher.
Only one provider is activated for a game session. LWJGL remains version-pinned by the selected Minecraft metadata and is not itself a graphics translation layer.

### Input architecture
O Engine defines platform-neutral input events and control bindings for touch, keyboard, mouse and gamepad devices. The resolver rejects duplicate physical bindings and invalid event types. An Android adapter now translates Android KeyEvent and MotionEvent keyboard, pointer, mouse, and controller events into that contract. It is not yet attached to the activity/game surface, persisted as user-editable control profiles, or connected to Minecraft/LWJGL input APIs.

### Native execution helper
MojoExec is an engineering reference for native process/JVM execution, not a dependency to blindly import. O Engine must own its process/JVM lifecycle, environment setup, library search paths, signal/exit handling, logging, cancellation and crash reporting. The current Rust JNI hook remains a separate native-loading milestone; the JVM is not yet started in-process.

### Authentication providers
Microsoft authentication is implemented as a device-code flow and offline profiles are stored locally. Ely.by is an optional future account provider and must use its documented authentication/session protocol; it must not be presented as a Microsoft-authenticated account or silently substituted into Mojang's official authentication flow. Account provider, identity, token storage and launch-session construction must remain explicit and separate.

These components are architecture requirements, not claims that every provider or feature is already operational. Each provider must pass artifact, ABI, license, lifecycle and real-device tests before it is offered as launch-ready.


## Current in-process Minecraft JVM launch path (implementation status)

The current launch path is connected across Compose, Kotlin IPC, and Rust native code:

1. The launcher prepares a version-specific launch plan and authenticated game arguments.
2. The :game Android service prepares the LTW/Android GLFW environment before HotSpot starts.
3. The service validates the runtime, staged LWJGL native modules, renderer alias, and libjvm path.
4. The Rust bridge retains the selected JVM library and creates one HotSpot VM with the JNI Invocation API.
5. The VM bootstrap verifies Java core classes and runs a small Java main smoke test.
6. The Rust bridge installs the Java-major-specific native-library hook; Kotlin then installs the O Engine LWJGL resolver hook against the exact game class.
7. After the game surface is ready, the authenticated Minecraft main class is invoked on the same HandlerThread that created HotSpot.
8. Main-class lookup and thrown Java exceptions are returned as bounded stack-trace diagnostics. The Kotlin IPC client rejects invalid class names, NUL-containing arguments, oversized individual arguments, and aggregate argument payloads above 512 KiB before Binder dispatch.

This is an implementation path, not proof of a playable game. Remaining launch gates include successful Android CI for the current revision, physical-device JVM creation with each supported runtime/ABI, verified LTW symbol routing through the exact Minecraft LWJGL binaries, a working game window and title screen, gameplay/input/audio validation, and coordinated JVM shutdown/recovery. No claim of successful Minecraft gameplay should be made until those checks pass.


## LTW Android render-target launch gate

Minecraft main dispatch now requires all three runtime prerequisites in the isolated game process: HotSpot created, the exact LTW/LWJGL native-loader hook installed, and a live Android Surface attached and made current through the LTW-owned EGL session. The service records the active GLES version and renderer before dispatching Minecraft main. Surface detach or attach failure clears the render-target-ready state, so a stale or lost Android window cannot be mistaken for a playable target.

The Rust native CI also verifies that the LTW EGL JNI entry points are exported by each Android ABI shared library: session create/destroy, window attach/detach/queue, pending-window processing, buffer swap, and EGL diagnostics. This catches missing native symbols at build time instead of waiting for an Android UnsatisfiedLinkError.

A passing native build and exported-symbol check still do not prove the first Minecraft title screen. That final gate requires an Android device/runtime combination and successful Minecraft rendering.
