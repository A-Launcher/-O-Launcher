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
