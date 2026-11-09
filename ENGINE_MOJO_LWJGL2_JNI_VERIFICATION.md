# Legacy LWJGL2 JNI export verification

The pinned Mojo LWJGL2 port has a different native ABI from LWJGL3. A valid ELF
machine/ABI and the three expected Java classes do not prove that the native
library implements the Java native methods.

Run the export check against each ABI's extracted `liblwjgl.so` or
`liblwjgl64.so`:

```sh
python3 native/tools/verify_mojo_lwjgl2_jni.py path/to/liblwjgl64.so --nm llvm-nm
```

The verifier checks the JNI lifecycle entry points for `GLFWDisplay`,
`GLFWContextImplementation`, and `GLFWPeerInfo`, and checks that the display
class's direct-buffer native accessors are present.

A pass is only a static export-table result. It does not establish that:
- the library's dependent GLFW C symbols resolve in Android/Bionic;
- the loaded library and Java artifact came from the same pinned release;
- the supplied window handle is a real `GLFWwindow*` (not an Android
  `ANativeWindow*` or EGLSurface);
- the context is shared with the selected GL4ES/EGL renderer;
- input events are polled on the GLFW owner thread; or
- Minecraft presents a frame on a physical Android device.

Keep the legacy launch gate enabled until dependency resolution, context/surface
ownership, event delivery, and an on-device rendered-frame test are all verified.
