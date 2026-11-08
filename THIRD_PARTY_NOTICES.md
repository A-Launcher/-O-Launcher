# Third-party notices

## MojoLauncher source integration

O Launcher includes the unmodified MojoLauncher source repository as a Git
submodule at `third_party/MojoLauncher`, pinned to commit
`1b8d90424c1cb791321d45f60ac62c54f4b013fa` from the upstream
`v3_openjdk` branch:

https://github.com/MojoLauncher/MojoLauncher/tree/1b8d90424c1cb791321d45f60ac62c54f4b013fa

MojoLauncher is licensed under **GNU Lesser General Public License, version 3
(LGPL-3.0)**. Its source files are kept unchanged in the submodule; O Launcher's
integration code is maintained separately. The exact upstream license is copied
into the installed application's `assets/licenses/LGPL-3.0-MojoLauncher.txt`
by Android CI. When distributing O Launcher, retain the applicable license,
notices, and source-availability information required by LGPL-3.0.

O Launcher uses this pinned source as the reference implementation for the
Minecraft version/instance installer, Fabric, Quilt, Forge, NeoForge, OptiFine,
modpack installation, and custom-control editor. Reuse of any individual
upstream component is subject to its own dependencies and third-party license;
the MojoLauncher repository's top-level LGPL license does not replace those
component licenses.

## MojoLauncher Forge/OptiFine installer agent

O Launcher builds the Java installer agent from the unmodified
`forge_installer/` module in the pinned MojoLauncher source submodule at
`third_party/MojoLauncher` (commit
`1b8d90424c1cb791321d45f60ac62c54f4b013fa`). The resulting
`forge_installer.jar` is packaged as an application asset for the Java
installer runtime. The agent source is covered by MojoLauncher's LGPL-3.0
license; its shaded `org.json` dependency retains its own license notices.
The agent is now staged and handed to the pinned Mojo AWT installer process using
Mojo's `javaArgs` and `modPath` contract. The O Launcher process adapter binds
the selected instance before AWT startup and receives the JVM exit callback for
profile discovery. End-to-end Forge/NeoForge installation is still unverified
until the Android build and a real installer run succeed.


## Mojo Caciocavallo AWT runtime

O Launcher packages the Java 8 Caciocavallo components from
[MojoLauncher/caciocavallo](https://github.com/MojoLauncher/caciocavallo/tree/d0efb5aaba)
and the Java 17+ Caciocavallo component from
[MojoLauncher/caciocavallo17](https://github.com/MojoLauncher/caciocavallo17/tree/411454b006).
The CI workflow verifies that each downloaded JAR is a readable archive and
includes the corresponding upstream **GPL-2.0** license texts in
`assets/licenses/GPL-2.0-Caciocavallo.txt` and
`assets/licenses/GPL-2.0-Caciocavallo17.txt`.

The AWT runtime module compiles the pinned MojoLauncher Java sources as an
Android library without modifying the upstream submodule. O Launcher's activity
adapter initializes the instance-specific Mojo storage paths before the AWT
activity starts; a replacement for Mojo's launcher-specific exit callback
returns installer status to O Launcher. Packaging and source integration do
not prove successful Forge/NeoForge installation or device-side Java execution.

## LTW

O Launcher packages the native LTW OpenGL compatibility library from the upstream
[MojoLauncher/LTW project](https://github.com/MojoLauncher/LTW), pinned to commit
`3f1f4df6c80c925ea34067b1d943a84acce0184d`.

LTW is licensed under **GNU Lesser General Public License, version 3 (LGPL-3.0)**.
The upstream license text and the GNU GPL version 3 license text are included in
the installed application's `assets/licenses/` directory. The pinned upstream
source is available at:

https://github.com/MojoLauncher/LTW/tree/3f1f4df6c80c925ea34067b1d943a84acce0184d

O Launcher builds the AAR from that source in CI and packages `libltw.so` as a
separate shared library. The launcher copies the packaged library into its private
renderer directory before loading it, rather than statically linking LTW into
O Engine's Rust native library.

LTW remains an upstream component; O Engine's launcher orchestration and native
LWJGL loader hook are separate O Launcher code.

## GL4ES Android compatibility library

O Launcher packages the four ABI-specific `libgl4es_114.so` binaries from
[MojoLauncher/MojoLauncher](https://github.com/MojoLauncher/MojoLauncher), pinned
to source commit `1b8d90424c1cb791321d45f60ac62c54f4b013fa`. The CI workflow
verifies each downloaded binary against its upstream Git blob SHA before packaging.

GL4ES is distributed under the MIT license. The corresponding upstream license
text is included as `assets/licenses/LICENSE-gl4es.txt`. Upstream source and
binary paths are recorded in `ENGINE_UPSTREAM_COMPONENTS.md`.

The libraries are packaged as Android ABI-specific native dependencies, but O
Engine has not yet activated GL4ES as a renderer or connected it to LWJGL 2's
native loading path. Packaging is not a claim of successful game rendering.


## Android GLFW platform backend

O Launcher now pins the Android platform port of GLFW from
[MojoLauncher/glfw](https://github.com/MojoLauncher/glfw), commit
`7a6c6ceb51f20cd0db84dec2914ac1a0237051f6`. This is an Android-specific GLFW 3.5
fork with Android window, EGL, input queue, monitor, and joystick platform code.
Its source retains the upstream GLFW zlib-style license in
`LICENSE.md`; the exact license is copied into the installed application's
`assets/licenses/LICENSE-Mojo-GLFW.txt`.

## MojoExec Android support library

The Android GLFW port depends on
[MojoLauncher/mojoexec](https://github.com/MojoLauncher/mojoexec), pinned to
commit `9ec4b836e93900135bad823eaac16ebc81cf6b98`. MojoExec is distributed under
the MIT license; its upstream license is copied into
`assets/licenses/LICENSE-MojoExec.txt`.

Both projects are pinned as Git submodules and built as separate shared libraries
for the four Android ABIs. O Engine's pinned native patch connects GLFW's poll/wait
event thread to the bounded Rust input queue and routes the Android Surface manager
through the LTW-owned EGL session. The separate Android LWJGL core/OpenGL JNI
modules are packaged from the pinned Pojav source described below. These are
source/build integrations; physical-device Minecraft rendering is not yet verified.


## Android LWJGL native runtime

O Launcher packages Android ABI-specific LWJGL native modules from
[PojavLauncherTeam/PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher),
pinned to commit
`b12ad048157b3aa255d078c235dd4571e1900309`. The CI workflow verifies each
binary against its Git blob SHA before packaging. The required core
`liblwjgl.so` and OpenGL `liblwjgl_opengl.so` modules are packaged for all
four supported Android ABIs; optional NanoVG, STB and TinyFD modules from the
same pinned build are included for mod/runtime compatibility.

These are Android-native builds, not Mojang's Linux native classifier archives.
The LWJGL license text and the pinned PojavLauncher project license are included
in `assets/licenses/LICENSE-LWJGL.txt` and
`assets/licenses/LICENSE-PojavLauncher.txt`. This integration supplies native
runtime prerequisites; it does not claim that Minecraft has launched or
presented a frame on a device.
