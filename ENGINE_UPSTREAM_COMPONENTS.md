# Upstream component evaluation

This document records the pinned upstream reference reviewed for O Engine integration. It distinguishes reusable components from code that remains reference-only. Do not import source or binaries without preserving the applicable license, notices, and source-offer/relinking obligations.

## MojoLauncher reference

- Repository: https://github.com/MojoLauncher/MojoLauncher
- Branch: `v3_openjdk`
- Inspected commit: `1b8d90424c1cb791321d45f60ac62c54f4b013fa`
- Project license: GNU LGPL-3.0 (see upstream `LICENSE`).
- Scope: technical reference and candidate upstream component source. No MojoLauncher source files or binaries are currently vendored in O Launcher.

### High-value components to reuse or adapt

| Component | Upstream path | License / status | O Engine decision |
|---|---|---|---|
| JVM lifecycle/bootstrap | `app_mojo/src/main/jni/jre_launcher/` | MojoLauncher LGPL-3.0 | Highest priority candidate. Review as a separately replaceable native component; retain LGPL source and notices if integrated. |
| JDK 8/17/21 native-library loading hooks | `app_mojo/src/main/jni/jre_launcher/native_library_hook.c` | MojoLauncher LGPL-3.0 | O Engine now registers its own Rust JNI wrappers for the verified Java 8/17/21 signatures and preloads the target ELF dependency closure before calling the original libjava function. Device verification remains. |
| ELF dependency hinter | `app_mojo/src/main/jni/jre_launcher/elf_hinter.c` | MojoLauncher LGPL-3.0 | Its dependency-walk strategy informed an independent bounded Rust implementation in `native/oengine/src/elf_hinter.rs`; no upstream C source was copied. |
| LWJGL native loader hook | `app_mojo/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c` | MojoLauncher LGPL-3.0 | Technical reference. O Engine has a Rust hook and now resolves the target `DynamicLinkLoader` through the embedded HotSpot system class loader before registering `ndlopen(long,int)`. O Engine exposes a retained `dlopen(RTLD_NOW | RTLD_LOCAL)` path for a renderer shared object stored under its private renderer directory, matching MojoExec's renderer-handle visibility rather than exporting wrapper symbols globally, then passes that handle into the HotSpot hook installer. This is only a loading primitive: provider packaging, Android linker-namespace compatibility, renderer initialization, EGL interposition, and device validation remain pending. The native loader now returns the platform `dlerror()` detail through the Kotlin bridge so linker failures can be diagnosed instead of collapsed into a generic load failure. |
| Java argument/runtime setup | `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/JavaRunner.java` | MojoLauncher LGPL-3.0 | Use as a compatibility checklist for Java properties, environment, native paths and version-specific setup; keep O Engine typed launch-plan API. |
| Runtime/game orchestration | `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/GameRunner.java` | MojoLauncher LGPL-3.0 | Reference only; strongly coupled to Mojo account, instance, renderer and preference classes. |
| MojoExec | Git submodule `mojoexec` at `9ec4b836e93900135bad823eaac16ebc81cf6b98` | MIT (LICENSE inspected) | Best permissive-license candidate for Android linker-namespace/driver helpers; candidate for vendoring as its own native library. Not yet added to O Launcher. |
| GLFW Android port | Git submodule `glfw` at `7a6c6ceb51f20cd0db84dec2914ac1a0237051f6` | zlib, per upstream README | Candidate dependency, but integration must preserve the LWJGL ABI and window/input contract. Not yet added. |
| SDL | Git submodule `sdl` at `c63845bd49c5a7667d1de0f6134f85ed1ccc8889` | zlib, per upstream README | Candidate for input/audio/window plumbing if it fits O Engine's renderer architecture. Not yet added. |
| Authlib-Injector | Third-party component | AGPL-3.0, per upstream README | Candidate for Ely.by/Yggdrasil authentication. Keep isolated and review AGPL distribution obligations before packaging. |

## Source-level findings

- Mojo's JRE launcher is not a drop-in JNI file: it depends on its own argument/string-array utilities, abort/exit handling, class-loader hook code, ELF dependency preloader, and JNI exception types. Porting only `jre_launcher.c` would leave an incomplete and unsafe VM path.
- Its native-library hook explicitly branches across JDK 8, 17, and 21 internal signatures. O Engine now has a typed native-load compatibility profile for JDK 8, 17, and 21, including the two observed JDK 17 descriptors. Java 25 intentionally has no profile and fails closed until its internal contract is verified. The profiles are now used by the native bootstrap to register a version-specific `NativeLibraries.load`/`ClassLoader$NativeLibrary.load` wrapper after VM creation. The wrapper preloads the ELF dependency closure, then calls the original libjava native function. Java 17's two observed descriptors are both supported; Java 25 still fails closed.
- Mojo's ELF hinter preloads dependency chains with global visibility to work around Android linker behavior. O Engine has an independent Rust implementation using Goblin to parse ELF dependencies, with file-size, depth, and dependency-count limits, canonical-path cycle detection, and RAII handle cleanup. It searches `LD_LIBRARY_PATH`; Android linker namespace behavior and device compatibility still require physical-device verification.
- MojoExec is not a generic EGL wrapper: its namespace bypass discovers private linker functions and creates Android linker namespaces, while its Vulkan override installs interposition hooks. It is MIT-licensed, but those private-API techniques are Android-version-sensitive. Keep it optional and behind an O Engine adapter; do not load its namespace-bypass path by default.
- Mojo's JavaRunner also supplies Android-specific properties and disables the default POSIX_SPAWN process launch mechanism. These are valuable compatibility requirements. O Engine has now added the Android JVM properties to its typed bootstrap plan (Linux OS identity, Android OS version, English locale, JNA native path, FORK process launch mechanism, Log4j safety flag, Forge/loader GUI workarounds, LWJGL system allocator, Vulkan/SPVC library names, device timezone, and processor count) with unit-test coverage; it does not copy the tightly coupled JavaRunner class.

## Integration policy

1. Prefer independently maintained upstream libraries with permissive licenses (MIT, BSD, zlib, Apache-2.0) where technically suitable.
2. For MojoLauncher LGPL components, keep the component separable, include the exact license and copyright notices, publish corresponding source and modifications as required, and preserve users' ability to replace/relink the LGPL component.
3. Do not copy MojoLauncher UI, app architecture, account system, or tightly coupled Java classes into O Launcher.
4. Do not import prebuilt APKs, signing keys, or opaque native blobs. A separately licensed native library may be packaged only when its upstream revision, per-ABI binary identity, and exact license are pinned and verified.
5. Pin every integrated component to an immutable commit/release and record checksums for binary artifacts.
6. Before enabling full Minecraft launch, verify Android API level, ABI, JDK major, dependent shared libraries, linker namespace behavior, signal handling, exit/abort behavior, and clean shutdown on physical devices.
7. Keep the current Rust O Engine bridge as the owner of O Engine's public native API. Any upstream LGPL native runtime must sit behind a narrow adapter boundary.

## Immediate implementation order

1. Complete and test the isolated `:game` process IPC foundation. (Implemented; device verification remains.)
2. Add the isolated JNI VM-creation smoke test and a tiny Java-main acceptance test before Minecraft classes or renderers are loaded. (Implemented in code; device verification remains.)
3. Integrate and device-verify the Android ELF dependency preloader and version-specific native-library hooks. (Code path implemented in O Engine Rust; not yet device-verified.)
4. Integrate Android GLFW/SDL/native rendering dependencies only after the VM test and native-library path are stable.
5. Resolve the LWJGL hook target through the embedded HotSpot class loader (implemented); integrate a renderer provider, pass its retained handle, and verify interception end-to-end.


### Isolated renderer-hook IPC

Renderer loading and LWJGL hook installation are now exposed as `MSG_INSTALL_RENDERER_HOOK` / `MSG_INSTALL_RENDERER_HOOK_RESULT` through the `:game` process service. The request is handled by the same `HandlerThread` that created embedded HotSpot, checks that VM creation succeeded, and validates that the renderer file is under the supplied private renderer root before calling the native loader and HotSpot hook installer. The hook must be installed before invoking the Minecraft main class, because that call may enter the game loop and occupy the VM-owning thread. This IPC is infrastructure only: no packaged LTW provider is selected or activated automatically, and no end-to-end renderer/device test has passed yet.


## MojoLauncher LTW integration reference (verified source path)

Inspected the MojoLauncher/MojoLauncher v3_openjdk branch at commit 1b8d90424c1cb791321d45f60ac62c54f4b013fa, plus its MojoLauncher/mojoexec submodule.

The useful LTW path is small and specific; it does not require copying MojoLauncher's whole launcher:

1. GLESRenderSpec.LTWRenderSpec selects libltw.so, requires host GLES 3+, and identifies the LTW renderer.
2. GLESRenderSpec.setupEnvironment() chooses a host GLES provider. The provider can use Android's native EGL/GLES libraries or an ANGLE provider and supplies LIBGL_EGL / LIBGL_GLES overrides when needed.
3. GameRunner calls renderer environment setup and then maybeSetupRenderer() before starting the JVM.
4. MojoExec.prepareEgl("libltw.so", ...) loads the selected renderer through the configured native-library/namespace route and records the GLES context/version policy.
5. Mojo adds -Dorg.lwjgl.opengl.libname=libGLMojo.so to the JVM arguments. Its lwjgl_dlopen_hook.c intercepts LWJGL's DynamicLinkLoader.ndlopen(long,int); when LWJGL asks for libGLMojo.so, it returns the prepared renderer EGL handle. Other libraries go through dlopen.
6. LTW then owns the EGL wrapper path and tracks contexts created/made-current/destroyed through its exported entry points.

Relevant upstream files:
- app_mojo/src/main/java/git/artdeell/mojo/game/renderer/impl/GLESRenderSpec.java
- app_mojo/src/main/java/git/artdeell/mojo/game/renderer/extra/GLESProvider.java
- app_mojo/src/main/java/git/artdeell/mojo/game/renderer/GameRenderer.java
- app_mojo/src/main/java/git/artdeell/mojo/utils/jre/GameRunner.java
- app_mojo/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c
- mojoexec/mojoexec.c (submodule)

O Engine adaptation:
- Reuse the upstream LTW AAR built from the pinned MojoLauncher/LTW source; do not extract an opaque library from an APK or copy Mojo's whole application.
- Adapt the driver-provider selection and pre-JVM renderer preparation into O Engine's typed Kotlin renderer plan.
- Preserve the existing isolated :game process and Rust JNI hook, but make it behave like the verified Mojo sequence: set EGL/GLES environment and load/retain libltw.so before Minecraft's LWJGL classes initialize; supply the LWJGL OpenGL library alias libGLMojo.so; have the ndlopen hook return the retained LTW handle for that alias and delegate unrelated loads.
- Keep MojoExec's Android private-namespace bypass optional. Its source is MIT-licensed, but it relies on private linker APIs; only add that path behind an Android-version/device capability gate if ordinary app-namespace loading fails and a device test validates the fallback.
- Do not mark the renderer ready until the exact LWJGL version used by the selected Minecraft version resolves the alias, and EGL create/make-current/destroy plus GL function lookup are verified on a physical device.

This source inspection resolves the previously unknown LWJGL alias/handle handoff. It does not by itself prove O Engine's current native hook is wired with the same timing or that LTW renders Minecraft on a device.

### LWJGL 2 native loading is not the LWJGL 3 alias contract

The upstream LWJGL 2.9.4 `org.lwjgl.Sys` source loads the core JNI library named `lwjgl` with `System.loadLibrary()`, unless `org.lwjgl.librarypath` is set, in which case it calls `System.load()` on the mapped `lwjgl` filename. It does not use LWJGL 3's `org.lwjgl.opengl.libname` property or `DynamicLinkLoader.ndlopen(long,int)` hook. Consequently, setting `-Dorg.lwjgl.opengl.libname=libGLMojo.so` cannot redirect LWJGL 2 to GL4ES. Redirecting the core JNI library alone would also not prove OpenGL function lookup is routed through GL4ES. O Engine's resolver now inspects `org/lwjgl/Sys.class` for the legacy `org.lwjgl.librarypath` / `System.load` surface and reports both blockers explicitly; the actual GL4ES/LWJGL 2 adapter remains unimplemented.


### O Engine staged LTW startup implementation

The game-process renderer sequence is now split to match MojoLauncher's observed ordering:

- `MSG_PREPARE_RENDERER` validates the private renderer path, applies optional `LIBGL_EGL` / `LIBGL_GLES` overrides, and loads/retains the renderer before `JNI_CreateJavaVM`.
- `MSG_INSTALL_RENDERER_HOOK` runs only after HotSpot is created and registers the LWJGL `DynamicLinkLoader.ndlopen(long,int)` replacement using the retained renderer handle.
- `OEngineJvmBootstrapPlanner.prepare(..., rendererLibraryAlias = "libGLMojo.so")` can add the corresponding `-Dorg.lwjgl.opengl.libname=libGLMojo.so` JVM property before VM creation. The alias is validated as a shared-library filename.

This is now a staged infrastructure path, not yet a complete game launch. The caller still needs to invoke renderer preparation before JVM creation, pass the matching alias into the JVM plan, install the hook after VM creation but before loading Minecraft classes, and then prove the selected LWJGL binary uses the expected native method. CI compilation and physical-device graphics validation are separate gates.


### LTW packaging and launch wiring

The Android CI pipeline now checks out the pinned LTW source revision, builds its release AAR, and places that AAR on the app compile/package classpath. APK verification requires `libltw.so` for all four configured Android ABIs. The APK includes the upstream LGPL-3.0 license text, GNU GPL-3.0 license text, and `THIRD_PARTY_NOTICES.md`.

At runtime, `OEngineLtwArtifactInstaller` copies the ABI-matched `libltw.so` from Android's extracted native-library directory into `files/oengine/renderers/ltw/<abi>/`, verifies the copied bytes against the packaged source with SHA-256, and validates the ELF identity and pinned provenance metadata.

The Installations diagnostic button now sequences:
1. Resolve the selected Minecraft classpath and require LWJGL 3's `org.lwjgl.system.linux.DynamicLinkLoader` plus the `ndlopen(long,int)` signature.
2. Prepare the JVM options with `-Dorg.lwjgl.opengl.libname=libGLMojo.so`.
3. Bind the isolated `:game` process and preload LTW before `JNI_CreateJavaVM`.
4. Create HotSpot, then register O Engine's retained-handle `ndlopen` hook on the VM-creating HandlerThread.
5. Leave Minecraft main invocation as a separate explicit step, after the hook reports success.

This wires the loader sequence but does not establish end-to-end graphics compatibility. Android-compatible Minecraft native extraction, real EGL surface/context behavior, game input/render-surface handoff, and a physical-device Minecraft launch test remain open gates.


## Minecraft native archive staging

Mojang Linux native classifier archives are downloaded with the version dependencies and verified against their metadata hashes and sizes. O Engine now safely extracts their shared objects into `natives/<version>/linux-archive` for inspection, rejects archive path traversal, and reports the number of extracted files.

This directory is diagnostic-only. A Linux ELF file is not an Android native library, even when its CPU architecture matches the device: Linux/glibc dependencies and Android/Bionic are different ABIs. These files are deliberately not added to the Android runtime native search path and are not passed to `System.loadLibrary`. Android-compatible LWJGL and Minecraft native replacements, dependency resolution, and device validation remain separate required work.


## Native ELF compatibility diagnostics

After Linux native classifiers are extracted to the diagnostic-only folder, O Engine writes `oengine-compatibility-report.json`. It records each shared object's ELF class, machine/Android ABI mapping, and common glibc markers such as `GLIBC_`, `libc.so.6`, and `ld-linux`.

The report deliberately distinguishes likely glibc binaries, ABI mismatches, invalid ELF files, and architecture-matching files whose Android compatibility remains unverified. It is a triage aid only: it does not prove Bionic compatibility, resolve transitive dependencies, or authorize loading these files. The launcher continues to keep the Linux archive folder out of the runtime native search path.


### Pinned Android GL4ES compatibility binaries

O Engine's Android CI now downloads the ABI-specific `libgl4es_114.so` shared libraries from MojoLauncher/MojoLauncher at immutable commit `1b8d90424c1cb791321d45f60ac62c54f4b013fa`:

| Android ABI | Upstream Git blob SHA-1 |
|---|---|
| arm64-v8a | `964c4a7714bad3011dd59e6f832c958a7513211a` |
| armeabi-v7a | `caf2962636decc6af10ec8d0e797ac2484cdd0d7` |
| x86 | `b21cc6eecc1cef296c65b50724a5bb4315c61e8b` |
| x86_64 | `3c23abbd4f9e0304ab084f88d08f53b9624ecda0` |

The workflow checks each file with `git hash-object` against the upstream blob identity before Android packaging, and checks that all four ABI libraries are present in the APK. It also verifies the upstream GL4ES MIT license file against its Git blob identity and installs it under `assets/licenses/LICENSE-gl4es.txt`.

This is a real Android-native dependency package, not a runtime renderer integration yet. GL4ES is not currently selected or loaded by O Engine, and LWJGL 2's native loading path is still blocked pending an adapter. LTW remains the active integration target for the LWJGL 3 path. Do not report GL4ES as a working game renderer until the LWJGL 2 loader bridge, EGL setup, GL entry points, and physical-device rendering have been validated.

O Engine now has a separate `prepareGl4es` launch preflight. It models the upstream GLES 2 minimum and GL4ES compatibility environment variables without applying LTW's GLES 3 requirement or claiming the LWJGL 2 bridge exists. The plan remains blocked unless the caller explicitly supplies both a verified GL4ES-specific LWJGL 2 loader capability and a verified wrapper-owned EGL/surface/GL-entry-point lifecycle. Unit tests cover GLES 2 acceptance, GLES 1 rejection, environment separation, missing-loader blocking, and missing-EGL-lifecycle blocking.


### GL4ES Android artifact staging and game-process route

- Pinned GL4ES Android shared objects are fetched from the inspected MojoLauncher revision and checked against their exact Git blob IDs during CI; the MIT license text is packaged.
- `OEngineGl4esArtifactInstaller` copies the ABI-matched `libgl4es_114.so` from the APK native-library directory into O Engine's private renderer directory, checks the copy against the packaged bytes, and validates its ELF class/machine and private-path boundary.
- The artifact installer can stage and ELF-check the pinned GL4ES library, but the isolated game-process renderer IPC deliberately rejects GL4ES. The existing `libGLMojo.so` retained-handle hook is the LTW/LWJGL 3 route and must not be assumed to implement GL4ES's LWJGL 2 loading contract.
- GL4ES remains catalogued and packaged only. A separate LWJGL 2 adapter, EGL initialization/context lifecycle, GL function resolution, and physical-device rendering test are required before it can be preloaded or selected.
- Upstream reference: `GLESRenderSpec.GL4ESRenderSpec` selects `libgl4es_114.so` with GLES 2; `GLESRenderSpec.setupRenderer()` delegates EGL preparation to MojoExec. O Engine has not imported MojoExec's private linker-namespace bypass and does not assume its absence is harmless on every Android version.
- MojoExec's inspected implementation stores the renderer library path, loads that library with `RTLD_LOCAL | RTLD_NOW`, and sets the renderer's GLES-context policy/version; it does not itself create the Android window surface. The renderer wrapper's EGL entry points and the game's surface lifecycle still need to be connected and tested. O Engine's existing `OEngineEglSession` creates a native Android GLES window context and is explicitly not a desktop-GL/LWJGL bridge, so it must not be substituted for GL4ES's wrapper-owned EGL path.


## Android window and LWJGL bridge source audit (2026-11-07)

The following source paths were inspected specifically to design the missing Android Surface-to-LWJGL window bridge. This is a source-level assessment, not a claim that the bridge is integrated.

### PojavLauncher

- Repository: https://github.com/PojavLauncherTeam/PojavLauncher
- Branch: `v3_openjdk`
- Inspected tree: `b12ad048157b3aa255d078c235dd4571e1900309`
- Repository license: LGPL-3.0; individual files and bundled components still require their own notice review.
- Relevant paths:
  - `app_pojavlauncher/src/main/jni/egl_bridge.c`
  - `app_pojavlauncher/src/main/jni/ctxbridges/egl_loader.c`
  - `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java`
  - `app_pojavlauncher/src/main/java/org/lwjgl/glfw/CallbackBridge.java`
  - `jre_lwjgl3glfw/src/main/java/org/lwjgl/opengl/PojavRendererInit.java`
  - `jre_lwjgl3glfw/src/main/java/org/lwjgl/glfw/CallbackBridge.java`

Important implementation findings:

1. Pojav does not treat an Android `Surface` as a complete GLFW window. Its JNI bridge converts the Java Surface to an `ANativeWindow` with `ANativeWindow_fromSurface`, retains/releases that native window explicitly, and calls its native window/context bridge setup.
2. The native EGL bridge owns the EGL window/context lifecycle and resolves EGL entry points, including `eglCreateWindowSurface`, `eglMakeCurrent`, and `eglSwapBuffers`. The GLFW Android port and bridge table connect the game-facing LWJGL API to this native implementation.
3. The Java-side `MinecraftGLSurface` coordinates Surface creation, resizing, destruction, touch/mouse/keyboard/gamepad input, and game readiness. Its input callbacks are connected to the LWJGL `CallbackBridge`, not merely stored in an Android event queue.
4. Renderer initialization is an additional integration point: Pojav's `PojavRendererInit` identifies the selected GL library from LWJGL's function provider or `org.lwjgl.opengl.libname` and invokes renderer-specific initialization for GL4ES.

### MojoLauncher

- Repository: https://github.com/MojoLauncher/MojoLauncher
- Branch: `v3_openjdk`
- Inspected commit: `1b8d90424c1cb791321d45f60ac62c54f4b013fa`
- Repository license: LGPL-3.0.
- Relevant paths:
  - `app_mojo/src/main/java/git/artdeell/mojo/game/render/SurfaceProvider.java`
  - `app_mojo/src/main/java/git/artdeell/mojo/game/render/SurfaceViewSurfaceProvider.java`
  - `app_mojo/src/main/java/git/artdeell/mojo/game/render/TextureViewSurfaceProvider.java`
  - `app_mojo/src/main/java/git/artdeell/mojo/game/platform/backend/PlatformBackend.java`
  - `app_mojo/src/main/java/git/artdeell/mojo/game/platform/backend/GLFWBackend.java`
  - `app_mojo/src/main/java/git/artdeell/mojo/game/renderer/impl/GLESRenderSpec.java`

Important implementation findings:

1. Mojo separates Android Surface acquisition from the platform backend through a small `SurfaceProvider` callback contract.
2. The GLFW backend forwards Surface create/update/destroy to native GLFW methods (`nativeSurfaceCreated`, `nativeSurfaceUpdated`, `nativeSurfaceDestroyed`) and routes mouse, keyboard, scroll, visibility and cursor state to GLFW.
3. Surface resize is a first-class event, distinct from Surface creation. Both SurfaceView and TextureView providers report lifecycle changes.
4. Mojo's GLES renderer environment is configured before JVM startup, including the LTW compatibility flags already documented above.

### DroidBridge Launcher

- Public source URL identified: https://github.com/DNAMobileApplications/DroidBridgeLauncherGplayGithub
- The public repository endpoint was not retrievable through the connected GitHub source reader during this audit (404). Its public project description identifies a mixed-origin source tree; DNA Mobile Applications-authored files may have separate rights, while third-party-derived files remain governed by their upstream licenses.
- Decision: use only publicly inspectable technical documentation and independently licensed upstream components until the exact source files and their individual license notices can be verified. The user's permission to use a project as a reference does not replace the copyright holder's license for proprietary or separately owned files.

### O Engine integration decision

The current O Engine Java EGL bootstrap creates and attaches an EGL window surface, but it does not yet expose that surface to LWJGL's GLFW implementation, provide a native window bridge, or present frames with `eglSwapBuffers`. The next implementation stage must add a narrow native-window/GLFW adapter with explicit create, resize, destroy and input callbacks. It must also define who owns the EGL display/context/surface and the `ANativeWindow` reference; O Engine must not create a second unrelated EGL context and assume Minecraft will render into it.

Do not port a single Pojav C function in isolation: its bridge depends on the matching bridge table, EGL loader, renderer-specific behavior, GLFW Android port, JNI declarations and lifecycle ownership. Any Pojav- or Mojo-derived code integrated into O Launcher must retain the LGPL-3.0 notices and comply with the applicable source/relinking obligations. Prefer a separately built, replaceable native bridge library and keep O Engine's Kotlin/Rust public APIs independent of upstream package names.
