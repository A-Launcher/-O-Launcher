package git.artdeell.dnbootstrap.glfw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.Surface;
import java.nio.ByteBuffer;

/**
 * ART-side JNI bootstrap for the pinned Mojo Android GLFW backend.
 *
 * Minecraft's LWJGL Java bindings execute in embedded HotSpot; this class only
 * provides Android Surface and platform callback endpoints expected by Mojo's
 * native Android port. The native library is loaded from O Engine's staged path.
 */
public final class GLFW {
    private GLFW() {}

    private static volatile boolean initialized;
    private static volatile Context applicationContext;
    private static volatile boolean cursorGrabbed;
    private static volatile double cursorX;
    private static volatile double cursorY;

    public static synchronized void initializeOnce(Context context) {
        if (context != null) applicationContext = context.getApplicationContext();
        if (initialized) return;
        initialize();
        initialized = true;
    }

    // Called by Mojo's native initialize() to cache the ART JavaVM and callbacks.
    public static void receiveGrabState(boolean grabbing) {
        cursorGrabbed = grabbing;
    }

    public static void receiveCursorPos(double x, double y) {
        cursorX = x;
        cursorY = y;
    }

    public static GLFWCursor loadCursor(ByteBuffer image, int width, int height, int xhot, int yhot) {
        // Android's pointer-icon adapter is not available until a host View is attached.
        return null;
    }

    public static void useCursor(GLFWCursor cursor) {}

    public static String getClipboardString() {
        Context context = applicationContext;
        if (context == null) return "";
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence text = clip.getItemAt(0).coerceToText(context);
            return text == null ? "" : text.toString();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    public static void setClipboardString(String value) {
        Context context = applicationContext;
        if (context == null) return;
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("O Launcher", value == null ? "" : value));
            }
        } catch (RuntimeException ignored) {
            // Clipboard access can be restricted by Android while the app is backgrounded.
        }
    }

    public static void enableDirectGamepad(ByteBuffer buttons, ByteBuffer axes) {}
    public static void receiveInit() {}

    public static boolean isCursorGrabbed() { return cursorGrabbed; }
    public static double getCursorX() { return cursorX; }
    public static double getCursorY() { return cursorY; }

    public static native void initialize();
    public static native void nativeSurfaceCreated(Surface surface);
    public static native void nativeSurfaceUpdated();
    public static native void nativeSurfaceDestroyed();
}
