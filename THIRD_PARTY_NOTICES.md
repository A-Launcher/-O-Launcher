# Third-party notices

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
for the four Android ABIs. This packages the Android GLFW platform backend and
its support libraries; it does **not** yet mean Minecraft's runtime LWJGL JNI
library has been replaced or that the game event loop calls O Engine's input
queue. That integration remains a separate required step.


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
