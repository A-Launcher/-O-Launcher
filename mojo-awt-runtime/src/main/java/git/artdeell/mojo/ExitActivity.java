package git.artdeell.mojo;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Keep;

/** Replaces Mojo's launcher-specific exit UI with an O Launcher callback. */
@Keep
public final class ExitActivity {
    private static final String TAG = "OEngineMojoExit";
    private static volatile String pendingProfileKey;
    private static volatile String pendingFailureMessage;
    private static volatile boolean exitCallbackSent;

    private ExitActivity() {}

    @Keep
    public static void setPendingProfileKey(String profileKey) {
        pendingProfileKey = profileKey;
        exitCallbackSent = false;
    }

    @Keep
    public static boolean hasExitCallback() {
        return exitCallbackSent;
    }

    @Keep
    public static void setPendingFailureMessage(String message) {
        pendingFailureMessage = message;
    }

    @Keep
    public static void showExitMessage(Context context, int code, boolean isSignal) {
        exitCallbackSent = true;
        try {
            if (context != null) {
                Intent result = new Intent();
                result.setClassName(context.getPackageName(), "com.alauncher.olauncher.MainActivity");
                result.putExtra("oengine.mojo.exit_code", code);
                result.putExtra("oengine.mojo.exit_signal", isSignal);
                result.putExtra("oengine.mojo.profile_key", pendingProfileKey);
                result.putExtra("oengine.mojo.exit_message", pendingFailureMessage);
                result.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(result);
            }
        } catch (Throwable failure) {
            Log.e(TAG, "Could not return installer result to O Launcher", failure);
        } finally {
            System.exit(0);
        }
    }
}
