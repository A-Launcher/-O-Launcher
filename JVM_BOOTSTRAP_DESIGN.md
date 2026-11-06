# O Engine JVM Bootstrap Design

## Purpose

This document defines the next native-runtime milestone for O Engine. The current native bridge only loads and retains the selected Android OpenJDK `libjvm.so` and verifies that the two JNI Invocation API entry points are exported. It does not create a VM, execute Minecraft, or establish LWJGL/renderer integration.

The implementation must remain O Engine-owned. Other Android launchers may be studied to understand platform constraints, but their code is not copied or vendored.

## Runtime model

An Android app process already runs Android Runtime (ART). Minecraft requires a separate Android-compatible OpenJDK/HotSpot runtime. The JNI Invocation API specification says that multiple VMs in one process are not supported; Android launchers that host a second runtime therefore rely on platform-specific behavior and additional native integration. A successful `dlopen` or symbol lookup is not evidence that a second VM can safely start.

O Engine will isolate game execution behind a dedicated Android process (for example, `:game`) before enabling VM creation. This keeps a HotSpot crash, signal-handler change, or native-library failure from directly terminating the launcher UI process. It does not remove the ART/HotSpot coexistence problem inside the game process; that still requires device validation.

## Required bootstrap phases

1. **Resolve** — validate the selected runtime's Java major, device ABI, private runtime root, `libjvm.so`, classpath, working directory, game main class, JVM options, and application arguments.
2. **Prepare linker environment** — derive the runtime's actual library directories and validate every required shared object. Establish the Android-compatible load path before loading the VM. Do not assume changing `LD_LIBRARY_PATH` after process startup is sufficient; Android linker namespaces and API-level behavior must be handled explicitly.
3. **Load runtime** — load the selected `libjvm.so` with the visibility required by its dependent runtime libraries, resolve `JNI_CreateJavaVM`, and retain the handle for the entire VM lifetime. Do not silently switch runtimes after a handle has been retained.
4. **Create VM** — construct a versioned `JavaVMInitArgs` structure from validated UTF-8 option strings. Add launcher-owned exit/abort callbacks only after their lifetime and thread-safety contracts are implemented. Reject malformed options and never set `ignoreUnrecognized` merely to hide configuration errors.
5. **Bridge classes and native libraries** — keep ART-side JNI references separate from the game VM's `JNIEnv`. Resolve game classes using the game VM's class loader. Implement and test the JDK-version-specific native-library loading seam before enabling LWJGL interception.
6. **Run main** — construct the Java `String[]` from the exact application arguments and invoke the selected main class on the game VM. Capture Java exceptions and route logs to the instance log directory.
7. **Stop** — detach any native-created threads, request orderly game shutdown, destroy the game VM only when its lifecycle permits, release renderer/JVM resources in reverse order, and report exit status to the launcher process.

## Lifecycle and safety rules

- A game VM is single-session and single-owner. Concurrent create/destroy operations are forbidden.
- The native thread that creates the VM owns its initial `JNIEnv`; a `JNIEnv` must never be shared across threads.
- ART JNI references and game-VM JNI references are never interchangeable.
- Native callbacks passed through `JavaVMOption.extraInfo` must remain valid for the full VM lifetime.
- Signal handlers, process-wide environment, current working directory, and linker state are process-wide resources. Their changes must be isolated to the game process and restored only when restoration is valid.
- A native crash must leave a diagnostic record with the phase, runtime path, ABI, and last known action. Do not log access tokens or full authentication payloads.
- The JVM must not be started until the runtime's dependent shared libraries and Android linker behavior have been validated on physical devices for each supported ABI.
- A VM startup failure must not be reported as a Minecraft launch failure; preserve the exact phase and native/JVM error code.

## Compatibility seams

- JDK 8, 17, and 21 have different internal native-library loading implementations. O Engine must detect the actual runtime major and install only a verified adapter for that major.
- LWJGL native loading is a later phase. The current Rust `nativeInstallLwjglHook` entry point is deliberately dormant until the game class loader, renderer handle lifetime, and native-loader call path are ready.
- EGL/LTW context interposition is also a later phase. The current Android EGL session is not automatically an LTW-managed context.
- Mojang's Linux native classifier archives are not Android/Bionic-ready merely because they match the device CPU architecture.

## Acceptance gates

The JVM bootstrap milestone is not complete until all of the following pass:

1. Unit tests cover option normalization, invalid paths, duplicate sessions, state transitions, argument conversion, and failure cleanup.
2. Native CI builds and verifies the bridge for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
3. Device tests prove that each supported Android OpenJDK major loads its dependencies and creates a VM in the isolated game process.
4. Device tests execute a tiny Java main class, capture output, propagate a non-zero result, and shut down cleanly.
5. Repeated start/stop and deliberate VM-start failures do not crash or corrupt the launcher UI process.
6. Only after these gates pass may the engine proceed to class-loader/native-library interception and a vanilla Minecraft launch.

## Technical references

- JNI Invocation API specification: https://docs.oracle.com/en/java/javase/26/docs/specs/jni/invocation.html
- Android Runtime JNI guidance: https://developer.android.com/guide/practices/verifying-apps-art
- MojoLauncher reference implementation inspected for platform behavior (not copied): `app_mojo/src/main/jni/jre_launcher/jre_launcher.c` and `app_mojo/src/main/java/git/artdeell/mojo/utils/jre/JavaRunner.java` on its `v3_openjdk` branch. Review its LGPL-3.0 license before reusing any code; O Engine's implementation remains original.

## Isolated process foundation status

The first isolated-process slice is now present in the Android app:

- The manifest declares `OEngineGameProcessService` with `android:process=":game"` and `android:exported="false"`.
- `OEngineGameProcessService` hosts a Messenger endpoint in that process and answers a health-check message with its PID and phase.
- `OEngineGameProcessClient` binds from the launcher process, requests the handshake, reports a typed status, and owns the binding lifetime through `close()`.

This is only an IPC/process-isolation foundation. It does not yet load OpenJDK, create a VM, launch a Java main class, or connect Minecraft. A caller must also keep the client alive for as long as it needs the binding.

## Game-process JVM linker probe

The isolated process now exposes a second Messenger request for a JVM linker diagnostic. The launcher sends only the selected runtime home, game working directory, and canonical `libjvm.so` path. The service validates that the library is named `libjvm.so` and remains inside the selected runtime directory, prepares process-local `JAVA_HOME`, `HOME`, `TMPDIR`, and `LD_LIBRARY_PATH`, then calls the existing Rust native symbol probe from the `:game` process.

The diagnostic runs on a dedicated HandlerThread, has bounded IPC timeouts, and retains the validated library handle only in the game process. This still does not call `JNI_CreateJavaVM`, install JDK native-library hooks, or launch Minecraft. The Android linker may not honor all runtime environment changes on every API level; device verification is required.

The Rust diagnostic opens `libjvm.so` with `RTLD_NOW | RTLD_GLOBAL`, matching the upstream Android JVM bootstrap loading strategy so symbols from the selected runtime can participate in process-wide native resolution. This is only a loader visibility choice; it does not establish that all runtime dependencies are present or that VM creation is safe.

## Experimental HotSpot VM creation smoke test

The Installations diagnostics now include an explicit action that runs inside the private `:game` process. The service revalidates the selected runtime and canonical `libjvm.so` path, retains the library with global symbol visibility, applies the runtime environment paths, and forwards the typed VM options to the Rust JNI bridge. The bridge resolves `JNI_CreateJavaVM`, constructs JNI 1.8 `JavaVMInitArgs`, and attempts to create exactly one VM per game process. On success it resolves `java/lang/String` through the new VM's JNI environment as a minimal core-class smoke test.

This is deliberately experimental: Android ART is already present in the process, and HotSpot coexistence is non-standard and device/runtime dependent. A native crash can terminate the isolated `:game` process; the launcher UI is in a separate process and has a bounded 60-second result timeout. The smoke test does not yet invoke a Java `main` method, install JDK native-library hooks, or launch Minecraft. Do not report it as a working Minecraft launch until it has passed on real devices.
