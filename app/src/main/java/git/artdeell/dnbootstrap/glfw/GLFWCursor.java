package git.artdeell.dnbootstrap.glfw;

import android.view.PointerIcon;

/** Android pointer icon created from the pixel buffer supplied by Mojo's GLFW JNI callback. */
public final class GLFWCursor {
    private final PointerIcon pointerIcon;

    public GLFWCursor(PointerIcon pointerIcon) {
        if (pointerIcon == null) throw new IllegalArgumentException("pointerIcon");
        this.pointerIcon = pointerIcon;
    }

    public PointerIcon getPointerIcon() {
        return pointerIcon;
    }
}