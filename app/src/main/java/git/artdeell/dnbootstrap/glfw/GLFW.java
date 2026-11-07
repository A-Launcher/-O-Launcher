package git.artdeell.dnbootstrap.glfw;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;
import android.view.PointerIcon;
import android.view.View;
import android.os.Build;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import android.view.KeyEvent;
import android.view.MotionEvent;

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
    private static volatile View hostView;
    private static volatile GLFWCursor activeCursor;
    private static volatile ByteBuffer gamepadButtons;
    private static volatile FloatBuffer gamepadAxes;
    private static volatile boolean gamepadConnected;

    public static synchronized void initializeOnce(Context context) {
        if (context != null) applicationContext = context.getApplicationContext();
        if (initialized) return;
        initialize();
        initialized = true;
    }

    // Called by Mojo's native initialize() to cache the ART JavaVM and callbacks.
    public static void receiveGrabState(boolean grabbing) {
        cursorGrabbed = grabbing;
        View view = hostView;
        if (view == null) return;
        view.post(() -> {
            if (hostView != view || Build.VERSION.SDK_INT < 26) return;
            applyCursor(view, grabbing);
            if (grabbing) {
                if (view.hasWindowFocus()) view.requestPointerCapture();
            } else {
                view.releasePointerCapture();
            }
        });
    }

    /** Connects native cursor-grab state to the currently displayed game SurfaceView. */
    public static void attachHostView(View view) {
        hostView = view;
        if (view != null) {
            view.post(() -> {
                if (hostView != view) return;
                applyCursor(view, cursorGrabbed);
                if (Build.VERSION.SDK_INT >= 26 && cursorGrabbed && view.hasWindowFocus()) {
                    view.requestPointerCapture();
                }
            });
        }
    }

    public static void detachHostView(View view) {
        if (hostView != view) return;
        hostView = null;
        if (Build.VERSION.SDK_INT >= 26) {
            view.releasePointerCapture();
            applyCursor(view, false);
        }
    }

    public static void receiveCursorPos(double x, double y) {
        cursorX = x;
        cursorY = y;
    }

    public static GLFWCursor loadCursor(ByteBuffer image, int width, int height, int xhot, int yhot) {
        if (image == null || width <= 0 || height <= 0) return null;
        long byteCount = (long) width * (long) height * 4L;
        if (byteCount > Integer.MAX_VALUE || image.remaining() < byteCount) return null;
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            ByteBuffer pixels = image.duplicate();
            pixels.limit(pixels.position() + (int) byteCount);
            bitmap.copyPixelsFromBuffer(pixels);
            int hotX = Math.max(0, Math.min(width - 1, xhot));
            int hotY = Math.max(0, Math.min(height - 1, yhot));
            return new GLFWCursor(PointerIcon.create(bitmap, hotX, hotY));
        } catch (RuntimeException | OutOfMemoryError ignored) {
            if (bitmap != null) bitmap.recycle();
            return null;
        }
    }

    public static void useCursor(GLFWCursor cursor) {
        activeCursor = cursor;
        View view = hostView;
        if (view != null) view.post(() -> {
            if (hostView == view) applyCursor(view, cursorGrabbed);
        });
    }

    private static void applyCursor(View view, boolean grabbed) {
        if (Build.VERSION.SDK_INT < 24) return;
        PointerIcon icon;
        if (grabbed) {
            icon = PointerIcon.getSystemIcon(view.getContext(), PointerIcon.TYPE_NULL);
        } else if (activeCursor != null) {
            icon = activeCursor.getPointerIcon();
        } else {
            icon = PointerIcon.getSystemIcon(view.getContext(), PointerIcon.TYPE_DEFAULT);
        }
        view.setPointerIcon(icon);
    }

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

    public static synchronized void enableDirectGamepad(ByteBuffer buttons, ByteBuffer axes) {
        if (buttons == null || axes == null) return;
        ByteBuffer buttonView = buttons.duplicate().order(ByteOrder.nativeOrder());
        FloatBuffer axisView = axes.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
        if (buttonView.capacity() != 14 || axisView.capacity() != 6) return;
        gamepadButtons = buttonView;
        gamepadAxes = axisView;
    }

    /** Updates the direct GLFW joystick buffers from the validated Android input IPC event. */
    public static synchronized void updateGamepadEvent(int code, int action, float x, float y, float value) {
        if (!initialized) return;
        if (!gamepadConnected) {
            try {
                nativeNotifyGamepadConnected();
                gamepadConnected = true;
            } catch (UnsatisfiedLinkError ignored) {
                return;
            }
        }
        ByteBuffer buttons = gamepadButtons;
        FloatBuffer axes = gamepadAxes;
        if (buttons == null || axes == null) return;
        boolean pressed = action == 0 || action == 2;
        if (action == 0 || action == 1 || action == 2) {
            int button = gamepadButtonIndex(code);
            if (button >= 0) buttons.put(button, (byte) (pressed ? 1 : 0));
            if (code == KeyEvent.KEYCODE_BUTTON_L2) axes.put(4, pressed ? 1f : -1f);
            if (code == KeyEvent.KEYCODE_BUTTON_R2) axes.put(5, pressed ? 1f : -1f);
            return;
        }
        if (action != 3) return;
        if (code >= 0 && code <= 5) {
            axes.put(code, code >= 4 ? value * 2f - 1f : value);
        } else if (code == 6) {
            buttons.put(13, (byte) (x < -0.85f ? 1 : 0));
            buttons.put(11, (byte) (x > 0.85f ? 1 : 0));
            buttons.put(10, (byte) (y < -0.85f ? 1 : 0));
            buttons.put(12, (byte) (y > 0.85f ? 1 : 0));
        }
    }

    private static int gamepadButtonIndex(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return 0;
            case KeyEvent.KEYCODE_BUTTON_B: return 1;
            case KeyEvent.KEYCODE_BUTTON_X: return 2;
            case KeyEvent.KEYCODE_BUTTON_Y: return 3;
            case KeyEvent.KEYCODE_BUTTON_L1: return 4;
            case KeyEvent.KEYCODE_BUTTON_R1: return 5;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return 6;
            case KeyEvent.KEYCODE_BUTTON_START: return 7;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return 8;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return 9;
            case KeyEvent.KEYCODE_DPAD_UP: return 10;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return 11;
            case KeyEvent.KEYCODE_DPAD_DOWN: return 12;
            case KeyEvent.KEYCODE_DPAD_LEFT: return 13;
            default: return -1;
        }
    }

    public static void receiveInit() {}

    public static boolean isInitialized() { return initialized; }\n    public static boolean isCursorGrabbed() { return cursorGrabbed; }
    public static double getCursorX() { return cursorX; }
    public static double getCursorY() { return cursorY; }

    public static native void initialize();
    public static native void nativeSurfaceCreated(Surface surface);
    public static native void nativeSurfaceUpdated();
    public static native void nativeSurfaceDestroyed();
}
