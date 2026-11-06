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
| JDK 8/17/21 native-library loading hooks | `app_mojo/src/main/jni/jre_launcher/native_library_hook.c` | MojoLauncher LGPL-3.0 | Reuse/adapt only with explicit per-JDK signature tests. Current Rust LWJGL hook is not a substitute for this seam. |
| ELF dependency hinter | `app_mojo/src/main/jni/jre_launcher/elf_hinter.c` | MojoLauncher LGPL-3.0 | Candidate to reuse after hardening and validating Android linker namespace behavior. |
| LWJGL native loader hook | `app_mojo/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c` | MojoLauncher LGPL-3.0 | Technical reference. O Engine already has its own Rust hook implementation; do not copy duplicate hook code. |
| Java argument/runtime setup | `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/JavaRunner.java` | MojoLauncher LGPL-3.0 | Use as a compatibility checklist for Java properties, environment, native paths and version-specific setup; keep O Engine typed launch-plan API. |
| Runtime/game orchestration | `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/GameRunner.java` | MojoLauncher LGPL-3.0 | Reference only; strongly coupled to Mojo account, instance, renderer and preference classes. |
| MojoExec | Git submodule `mojoexec` at `9ec4b836e93900135bad823eaac16ebc81cf6b98` | MIT (LICENSE inspected) | Best permissive-license candidate for Android linker-namespace/driver helpers; candidate for vendoring as its own native library. Not yet added to O Launcher. |
| GLFW Android port | Git submodule `glfw` at `7a6c6ceb51f20cd0db84dec2914ac1a0237051f6` | zlib, per upstream README | Candidate dependency, but integration must preserve the LWJGL ABI and window/input contract. Not yet added. |
| SDL | Git submodule `sdl` at `c63845bd49c5a7667d1de0f6134f85ed1ccc8889` | zlib, per upstream README | Candidate for input/audio/window plumbing if it fits O Engine's renderer architecture. Not yet added. |
| Authlib-Injector | Third-party component | AGPL-3.0, per upstream README | Candidate for Ely.by/Yggdrasil authentication. Keep isolated and review AGPL distribution obligations before packaging. |

## Source-level findings

- Mojo's JRE launcher is not a drop-in JNI file: it depends on its own argument/string-array utilities, abort/exit handling, class-loader hook code, ELF dependency preloader, and JNI exception types. Porting only `jre_launcher.c` would leave an incomplete and unsafe VM path.
- Its native-library hook explicitly branches across JDK 8, 17, and 21 internal signatures. O Engine now has a typed native-load compatibility profile for JDK 8, 17, and 21, including the two observed JDK 17 descriptors. Java 25 intentionally has no profile and fails closed until its internal contract is verified. These profiles are metadata only; the actual `NativeLibraries.load` hook is not installed yet.
- Mojo's ELF hinter preloads dependency chains with global visibility to work around Android linker behavior. Its current upstream implementation has a fixed 512-entry stack, scans ELF dynamic sections, and relies on `LD_LIBRARY_PATH`; it needs bounds, parser, and error-path hardening before any shipping integration.
- MojoExec is not a generic EGL wrapper: its namespace bypass discovers private linker functions and creates Android linker namespaces, while its Vulkan override installs interposition hooks. It is MIT-licensed, but those private-API techniques are Android-version-sensitive. Keep it optional and behind an O Engine adapter; do not load its namespace-bypass path by default.
- Mojo's JavaRunner also supplies Android-specific properties and disables the default POSIX_SPAWN process launch mechanism. These are valuable compatibility requirements. O Engine has now added the Android JVM properties to its typed bootstrap plan (Linux OS identity, Android OS version, English locale, JNA native path, FORK process launch mechanism, Log4j safety flag, Forge/loader GUI workarounds, LWJGL system allocator, Vulkan/SPVC library names, device timezone, and processor count) with unit-test coverage; it does not copy the tightly coupled JavaRunner class.

## Integration policy

1. Prefer independently maintained upstream libraries with permissive licenses (MIT, BSD, zlib, Apache-2.0) where technically suitable.
2. For MojoLauncher LGPL components, keep the component separable, include the exact license and copyright notices, publish corresponding source and modifications as required, and preserve users' ability to replace/relink the LGPL component.
3. Do not copy MojoLauncher UI, app architecture, account system, or tightly coupled Java classes into O Launcher.
4. Do not import prebuilt APKs, signing keys, runtime binaries, or opaque native blobs.
5. Pin every integrated component to an immutable commit/release and record checksums for binary artifacts.
6. Before enabling full Minecraft launch, verify Android API level, ABI, JDK major, dependent shared libraries, linker namespace behavior, signal handling, exit/abort behavior, and clean shutdown on physical devices.
7. Keep the current Rust O Engine bridge as the owner of O Engine's public native API. Any upstream LGPL native runtime must sit behind a narrow adapter boundary.

## Immediate implementation order

1. Complete and test the isolated `:game` process IPC foundation. (Implemented; device verification remains.)
2. Add the isolated JNI VM-creation smoke test and a tiny Java-main acceptance test before Minecraft classes or renderers are loaded. (Implemented in code; device verification remains.)
3. Integrate the Android ELF dependency preloader and version-specific native-library hooks, reusing the pinned MojoLauncher LGPL component where appropriate.
4. Integrate Android GLFW/SDL/native rendering dependencies only after the VM test and native-library path are stable.
5. Connect the existing O Engine Rust LWJGL hook to the real game class loader and renderer handle lifecycle.
