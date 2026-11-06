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
