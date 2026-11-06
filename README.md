# O Launcher

An independent Android launcher for Minecraft: Java Edition, built around the original O Engine.

## Current development status

O Launcher is under active development. The Android launcher UI, Minecraft metadata/download foundation, runtime installer, account foundations, graphics/input preflight, and Rust native packaging are present. The game execution pipeline is not complete yet.

### Implemented foundations

- Landscape Android launcher UI and navigation.
- Minecraft version metadata, client/library/assets download and integrity checks.
- Per-instance game directories and profile/instance selection foundations.
- Android OpenJDK runtime download and validation for supported Java majors.
- Microsoft device-code authentication flow and encrypted refresh-token storage foundation.
- Offline account identity foundation.
- O Engine runtime/JVM bootstrap planning and native linker diagnostics.
- Rust native library built and packaged for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- Android input event adapter/queue and EGL capability preflight.
- Isolated `:game` process service and Messenger IPC health-check foundation.

### Not complete yet

- Creating and owning the OpenJDK VM inside the game process.
- Running a Java main class and launching Minecraft.
- LWJGL native-library interception connected to the real game class loader.
- A working desktop-OpenGL compatibility renderer and complete EGL/LTW lifecycle.
- Android-compatible replacements/ports for every required game native library.
- End-to-end keyboard, mouse, touch and controller delivery to LWJGL.
- Complete mod-loader installation, Ely.by authentication, and real-device compatibility testing.

A successful APK build or native-library packaging check is not evidence that Minecraft can launch. The project will only mark game launch complete after physical-device tests execute a vanilla Minecraft session.

## Architecture

- Kotlin and Jetpack Compose for Android UI, lifecycle, storage and orchestration.
- Rust for O Engine native contracts, JNI integration and native runtime components.
- Gradle Kotlin DSL and GitHub Actions for reproducible Android builds.

O Engine is an original implementation. Other launchers and upstream libraries may be used as technical references or integrated components only where their licenses permit it, with provenance and notices preserved.

## Build

Open the project in Android Studio with JDK 17, Android SDK 36, and the NDK version declared in `app/build.gradle.kts`. GitHub Actions builds the Rust native library for all configured Android ABIs before assembling the APK.

## Legal

O Launcher is an independent community project and is not affiliated with, endorsed by, or sponsored by Mojang or Microsoft. Minecraft is a trademark of Mojang Synergies AB.
