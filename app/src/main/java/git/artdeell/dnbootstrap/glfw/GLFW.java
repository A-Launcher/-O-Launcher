package git.artdeell.dnbootstrap.glfw;

import android.view.Surface;
import java.nio.ByteBuffer;

/**
 * ART-side JNI bootstrap for the pinned Mojo Android GLFW backend.
 *
 * Minecraft's LWJGL Java bindings execute in embedded HotSpot; this class only
 * provides the Android Surface and platform callback endpoint expected by Mojo's
 * native Android port. The native library is loaded by OEngineAndroidGlfwBootstrap
 * from its verified private staged path.
 */
public final class GLFW {
    private GLFW() {}

    private static volatile boolean initialized;

    public static synchronized void initializeOnce() {
        if (initialized) return;
        initialize();
        initialized = true;
    }

    // Called by Mojo's native initialize() to cache the ART JavaVM and callbacks.
    public static void receiveGrabState(boolean grabbing) {}
    public static void receiveCursorPos(double x, double y) {}
    public static GLFWCursor loadCursor(ByteBuffer image, int width, int height, int xhot, int yhot) {
        return null;
    }
    public static void useCursor(GLFWCursor cursor) {}
    public static String getClipboardString() { return ""; }
    public static void setClipboardString(String value) {}
    public static void enableDirectGamepad(ByteBuffer buttons, ByteBuffer axes) {}
    public static void receiveInit() {}

    public static native void initialize();
    public static native void nativeSurfaceCreated(Surface surface);
    public static native void nativeSurfaceUpdated();
    public static native void nativeSurfaceDestroyed();
}
