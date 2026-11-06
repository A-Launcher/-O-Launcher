# Upstream component evaluation

This document records the pinned upstream reference reviewed for O Engine integration. It distinguishes reusable components from code that remains reference-only. Do not import source or binaries without preserving the applicable license, notices, and source-offer/relinking obligations.

## MojoLauncher reference

- Repository: https://github.com/MojoLauncher/MojoLauncher
- Branch: `v3_openjdk`
- Inspected commit: `1b8d90424c1cb791321d45f60ac62c54f4b013fa`
- Project license: GNU LGPL-3.0 (see upstream `LICENSE`).
- Scope: technical reference and candidate upstream component source. No MojoLauncher source files or binaries are currently vendored by this document.

### High-value components to reuse or adapt

| Component | Upstream path | License / status | O Engine decision |
|---|---|---|---|
| JVM lifecycle/bootstrap | `app_mojo/src/main/jni/jre_launcher/` | MojoLauncher LGPL-3.0 | Highest priority candidate. Review as a separately replaceable native component; retain LGPL source and notices if integrated. |
| JDK 8/17/21 native-library loading hooks | `app_mojo/src/main/jni/jre_launcher/native_library_hook.c` | MojoLauncher LGPL-3.0 | Reuse/adapt only with explicit per-JDK signature tests. Current Rust LWJGL hook is not a substitute for this seam. |
| ELF dependency hinter | `app_mojo/src/main/jni/jre_launcher/elf_hinter.c` | MojoLauncher LGPL-3.0 | Candidate to reuse after hardening and validating Android linker namespace behavior. |
| LWJGL native loader hook | `app_mojo/src/main/jni/jvm_hooks/lwjgl_dlopen_hook.c` | MojoLauncher LGPL-3.0 | Technical reference. O Engine already has its own Rust hook implementation; do not copy duplicate hook code. |
| Java argument/runtime setup | `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/JavaRunner.java` | MojoLauncher LGPL-3.0 | Use as a compatibility checklist for Java properties, environment, native paths and version-specific setup; keep O Engine typed launch-plan API. |
| Runtime/game orchestration | `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/GameRunner.java` | MojoLauncher LGPL-3.0 | Reference only; strongly coupled to Mojo account, instance, renderer and preference classes. |
| MojoExec | Git submodule `mojoexec` | MIT, per upstream README | Candidate for native process/graphics helpers after exact submodule commit and license are verified. Not yet added. |
| GLFW Android port | Git submodule `glfw` | zlib, per upstream README | Candidate dependency, but integration must preserve the LWJGL ABI and window/input contract. Not yet added. |
| SDL | Git submodule `sdl` | zlib, per upstream README | Candidate for input/audio/window plumbing if it fits O Engine's renderer architecture. Not yet added. |
| Authlib-Injector | Third-party component | AGPL-3.0, per upstream README | Candidate for Ely.by/Yggdrasil authentication. Keep isolated and review AGPL distribution obligations before packaging. |

## Integration policy

1. Prefer independently maintained upstream libraries with permissive licenses (MIT, BSD, zlib, Apache-2.0) where technically suitable.
2. For MojoLauncher LGPL components, keep the component separable, include the exact license and copyright notices, publish corresponding source and modifications as required, and preserve users' ability to replace/relink the LGPL component.
3. Do not copy MojoLauncher UI, app architecture, account system, or tightly coupled Java classes into O Launcher.
4. Do not import prebuilt APKs, signing keys, runtime binaries, or opaque native blobs.
5. Pin every integrated component to an immutable commit/release and record checksums for binary artifacts.
6. Before enabling VM creation, verify Android API level, ABI, JDK major, dependent shared libraries, linker namespace behavior, signal handling, exit/abort behavior, and clean shutdown on physical devices.
7. Keep the current Rust O Engine bridge as the owner of O Engine's public native API. Any upstream LGPL native runtime must sit behind a narrow adapter boundary.

## Immediate implementation order

1. Complete and test the isolated `:game` process IPC foundation.
2. Integrate or adapt the smallest required JVM bootstrap component, including the ELF dependency preloader and version-specific native-library hooks.
3. Add a tiny Java-main acceptance test before Minecraft classes or renderers are loaded.
4. Integrate Android GLFW/SDL/native rendering dependencies only after the VM test is stable.
5. Connect the existing O Engine Rust LWJGL hook to the real game class loader and renderer handle lifecycle.
