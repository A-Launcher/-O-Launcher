package git.artdeell.mojoexec;

/**
 * Minimal ART-side JNI declaration for the pinned MojoExec Android support library.
 *
 * O Engine uses only the EGL preparation and native-library-directory hooks needed
 * by the GLFW Android backend. The declarations intentionally preserve the exact
 * upstream JNI package, class and method signatures.
 */
public final class MojoExec {
    private MojoExec() {}

    public static native boolean prepareEgl(
            String eglPath,
            boolean useBypass,
            boolean useGles,
            int glesVersion
    );

    public static native void setNativeLibraryDir(String dir);

    public static native void setUseBigCoreAffinity(boolean enable);
}