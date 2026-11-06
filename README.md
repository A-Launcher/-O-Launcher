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

#### Minecraft execution and graphics
- Creating and owning a stable OpenJDK VM inside the isolated game process on supported Android devices.
- Running the actual Minecraft main class through to a visible title screen and playable world.
- Verifying the LWJGL 3 `DynamicLinkLoader.ndlopen` hook against the exact Mojang-provided classpath and a real HotSpot VM. The hook implementation and diagnostics exist, but registration and redirection are not yet device-proven.
- Completing LTW's EGL/GLES setup, surface creation/resizing, swap/present loop, context loss/recreation, and lifecycle handoff.
- A separate, verified LWJGL 2 loading/EGL adapter for GL4ES. GL4ES is staged, not an active working renderer.
- Integrating or porting every Android-incompatible native dependency required by supported Minecraft versions.
- End-to-end keyboard, mouse, touch, scroll, gamepad/controller, clipboard, and window-resize delivery to the game.
- Audio output/input integration and reliable pause/resume/background lifecycle.
- Physical-device validation across Android versions, GPU vendors, and all packaged ABIs.

#### Launcher features
- Real mod-loader installation and launch integration (Fabric, Forge, NeoForge, and Quilt as applicable).
- Complete skin import, preview, local apply, and authenticated upload flow.
- Marketplace provider/API integration, browsing, downloads, and install management.
- Applying every settings control to the actual runtime, renderer, input, and game process.
- Fully isolated per-profile game directories, libraries/assets associations, and profile-specific install/update management.
- Complete Ely.by authentication and any additional account-provider flows.
- End-to-end Microsoft sign-in, refresh, entitlement checks, and launch verification with a real account.

A successful APK build, Rust test, or native-library packaging check is not evidence that Minecraft can launch. The project will only mark game launch complete after physical-device tests execute a vanilla Minecraft session, reach the title screen, create/join a world, and verify input, graphics, and audio.

## Architecture

- Kotlin and Jetpack Compose for Android UI, lifecycle, storage and orchestration.
- Rust for O Engine native contracts, JNI integration and native runtime components.
- Gradle Kotlin DSL and GitHub Actions for reproducible Android builds.

O Engine is an original implementation. Other launchers and upstream libraries may be used as technical references or integrated components only where their licenses permit it, with provenance and notices preserved.

### Technical references

- [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher), LGPL-3.0: used as a technical reference for Android GLES renderer setup, the LTW OpenGL ES 3 requirement, renderer environment defaults, and the Android game-surface lifecycle. O Engine's Rust JNI, process model, launcher UI, and renderer integration remain independently implemented.
- [MojoLauncher GLES renderer setup](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/app_mojo/src/main/java/git/artdeell/mojo/game/renderer/impl/GLESRenderSpec.java)
- [MojoLauncher Android surface lifecycle](https://github.com/MojoLauncher/MojoLauncher/tree/v3_openjdk/app_mojo/src/main/java/git/artdeell/mojo/game/render)

## Build

Open the project in Android Studio with JDK 17, Android SDK 36, and the NDK version declared in `app/build.gradle.kts`. GitHub Actions builds the Rust native library for all configured Android ABIs before assembling the APK.

## Legal

O Launcher is an independent community project and is not affiliated with, endorsed by, or sponsored by Mojang or Microsoft. Minecraft is a trademark of Mojang Synergies AB.
