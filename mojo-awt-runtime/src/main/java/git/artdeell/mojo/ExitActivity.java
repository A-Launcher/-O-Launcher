package git.artdeell.mojo;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Keep;

/**
 * Compatibility replacement for Mojo's launcher-specific exit UI.
 * The native JVM bridge resolves this exact class and method name.
 */
@Keep
public final class ExitActivity {
    private static final String TAG = "OEngineMojoExit";

    private static volatile String pendingProfileKey;\n\n    private ExitActivity() {}\n\n    public static void setPendingProfileKey(String profileKey) {\n        pendingProfileKey = profileKey;\n    }

    @Keep
    public static void showExitMessage(Context context, int code, boolean isSignal) {
        try {
            if (context != null) {
                Intent result = new Intent();
                result.setClassName(
                    context.getPackageName(),
                    "com.alauncher.olauncher.MainActivity"
                );
                result.putExtra("oengine.mojo.exit_code", code);
                result.putExtra("oengine.mojo.exit_signal", isSignal);
                result.putExtra(
                    "oengine.mojo.profile_key",
                    context.getSharedPreferences("o_launcher_profiles", Context.MODE_PRIVATE)
                        .getString("pending_mojo_profile", null)
                );
                result.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                );
                context.startActivity(result);
            }
        } catch (Throwable failure) {
            Log.e(TAG, "Could not return installer result to O Launcher", failure);
        } finally {
            // The AWT/JVM worker process must not remain alive after the VM exits.
            System.exit(0);
        }
    }
}
