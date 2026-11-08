plugins {
    id("com.android.library")
}

val mojoAwtJava = layout.buildDirectory.dir("generated/mojoAwtJava")
val prepareMojoAwtJava by tasks.registering(Sync::class) {
    from("../third_party/MojoLauncher/app_mojo/src/main/java")
    exclude("git/artdeell/mojo/ExitActivity.java")
    into(mojoAwtJava)
    doLast {
        // Upstream hard-codes /games/PojavLauncher on Android 26–28. O Launcher
        // must keep every supported API level inside the selected instance, so patch
        // only the generated build copy to use the Activity's scoped external-files root.
        val toolsSource = mojoAwtJava.get().file("git/artdeell/mojo/Tools.java").asFile
        val original = toolsSource.readText()
        val legacyStorage = """        if(SDK_INT >= 29) {
            return ctx.getExternalFilesDir(null);
        }
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        if(externalStorageDirectory == null) return null;
        // PojavLauncher is left for compatibility purposes
        File launcherRoot = new File(externalStorageDirectory,"games/PojavLauncher");
        if(!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState(launcherRoot))) return null;
        return launcherRoot;"""
        check(original.contains(legacyStorage)) {
            "Pinned Mojo Tools storage implementation changed; review the O Launcher instance-root patch."
        }
        val patched = original.replace(legacyStorage, "        return ctx.getExternalFilesDir(null);")
        check(patched != original) {
            "Mojo instance-scoped storage patch was not applied."
        }
        check(
            patched.contains("private static @Nullable File getAppStorageRoot(Context ctx)") &&
                patched.contains("return ctx.getExternalFilesDir(null);") &&
                !patched.contains("File launcherRoot = new File(externalStorageDirectory,\"games/PojavLauncher\");")
        ) {
            "Mojo instance-scoped storage patch output is invalid."
        }
        toolsSource.writeText(patched)

        // AGP library R.id values are not compile-time constants. Upstream uses
        // switch/case on these IDs, which fails javac; preserve behavior with equality checks.
        val awtActivity = mojoAwtJava.get()
            .file("git/artdeell/mojo/awt/AWTActivity.java").asFile
        val awtOriginal = awtActivity.readText()
        val mouseButtonSwitch = Regex(
            """        switch \(v\.getId\(\)\) \{\s+case R\.id\.installmod_mouse_pri:\s+Platform\.sendMouseButtonEvent\(MotionEvent\.BUTTON_PRIMARY, isDown \? 1 : 0, CallbackBridge\.getCurrentMods\(\)\);\s+break;\s+case R\.id\.installmod_mouse_sec:\s+Platform\.sendMouseButtonEvent\(MotionEvent\.BUTTON_SECONDARY, isDown \? 1 : 0, CallbackBridge\.getCurrentMods\(\)\);\s+break;\s+\}"""
        )
        val mouseButtonIf = """        if (v.getId() == R.id.installmod_mouse_pri) {
            Platform.sendMouseButtonEvent(MotionEvent.BUTTON_PRIMARY, isDown ? 1 : 0, CallbackBridge.getCurrentMods());
        } else if (v.getId() == R.id.installmod_mouse_sec) {
            Platform.sendMouseButtonEvent(MotionEvent.BUTTON_SECONDARY, isDown ? 1 : 0, CallbackBridge.getCurrentMods());
        }"""
        check(mouseButtonSwitch.containsMatchIn(awtOriginal)) {
            "Pinned Mojo AWT mouse-button switch changed; review the non-final resource ID patch."
        }
        var awtPatched = mouseButtonSwitch.replace(awtOriginal, mouseButtonIf)
        val windowMoveSwitch = """        if(isDown) switch(v.getId()) {
            case R.id.installmod_window_moveup:
                AWTBridge.nativeMoveWindow(0, -10);
                break;
            case R.id.installmod_window_movedown:
                AWTBridge.nativeMoveWindow(0, 10);
                break;
            case R.id.installmod_window_moveleft:
                AWTBridge.nativeMoveWindow(-10, 0);
                break;
            case R.id.installmod_window_moveright:
                AWTBridge.nativeMoveWindow(10, 0);
                break;
        }"""
        val windowMoveIf = """        if (isDown) {
            int viewId = v.getId();
            if (viewId == R.id.installmod_window_moveup) {
                AWTBridge.nativeMoveWindow(0, -10);
            } else if (viewId == R.id.installmod_window_movedown) {
                AWTBridge.nativeMoveWindow(0, 10);
            } else if (viewId == R.id.installmod_window_moveleft) {
                AWTBridge.nativeMoveWindow(-10, 0);
            } else if (viewId == R.id.installmod_window_moveright) {
                AWTBridge.nativeMoveWindow(10, 0);
            }
        }"""
        check(awtPatched.contains(windowMoveSwitch)) {
            "Pinned Mojo AWT window movement switch changed; review the non-final resource ID patch."
        }
        awtPatched = awtPatched.replace(windowMoveSwitch, windowMoveIf)
        check(!awtPatched.contains("case R.id.installmod_")) {
            "Mojo AWT resource ID switch patch was incomplete."
        }
        awtActivity.writeText(awtPatched)
    }
}

android {
    namespace = "git.artdeell.mojo"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        // Mojo's application sources reference this generated field; this module is an AAR.
        buildConfigField("String", "VERSION_NAME", "\"0.1.0\"")
        consumerProguardFiles("consumer-rules.pro")
        resValue("string", "application_package", "com.alauncher.olauncher")
        resValue("string", "storageProviderAuthorities", "com.alauncher.olauncher.scoped.gamefolder")
        resValue("string", "group_id", "com.alauncher")
        // Keep the upstream optional CurseForge client disabled; O Launcher does not expose it.
        resValue("string", "curseforge_api_key", "DUMMY")
    }

    buildFeatures {
        buildConfig = true
    }

    // Compile the pinned upstream Java sources with their original Java 8 language level.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets.getByName("main").apply {
        java.srcDir(mojoAwtJava)
        res.srcDir("../third_party/MojoLauncher/app_mojo/src/main/res")
        assets.srcDir("../third_party/MojoLauncher/app_mojo/src/main/assets")
        manifest.srcFile("src/main/AndroidManifest.xml")
    }
}

tasks.named("preBuild").configure { dependsOn(prepareMojoAwtJava) }

dependencies {
    api("androidx.annotation:annotation:1.10.0")
    api("androidx.appcompat:appcompat:1.7.1")
    api("androidx.activity:activity:1.10.1")
    api("androidx.core:core:1.16.0")
    api("androidx.fragment:fragment:1.8.8")
    api("androidx.preference:preference:1.2.1")
    api("androidx.drawerlayout:drawerlayout:1.2.0")
    api("androidx.viewpager2:viewpager2:1.1.0")
    api("androidx.constraintlayout:constraintlayout:2.2.1")
    api("androidx.lifecycle:lifecycle-runtime:2.9.2")

    api("javax.annotation:javax.annotation-api:1.3.2")
    api("commons-codec:commons-codec:1.19.0")
    api("commons-io:commons-io:2.19.0")
    api("org.apache.commons:commons-compress:1.28.0")
    api("androidx.recyclerview:recyclerview:1.4.0")
    api("org.tukaani:xz:1.10")
    api("net.sourceforge.htmlcleaner:htmlcleaner:2.29")

    // Match the pinned Mojo app's local exp4j/Gson patches and bundled support AARs.
    api(fileTree("../third_party/MojoLauncher/app_mojo/libs") {
        include("*.jar", "*.aar")
    })

    api("com.github.duanhong169:checkerboarddrawable:1.0.2")
    api("com.github.PojavLauncherTeam:portrait-sdp:ed33e89cbc")
    api("com.github.PojavLauncherTeam:portrait-ssp:6c02fd739b")
    api("com.github.Mathias-Boulay:ExtendedView:1.0.0")
    api("com.github.Mathias-Boulay:android_gamepad_remapper:2.0.3")
    api("com.github.Mathias-Boulay:virtual-joystick-android:1.14")
    api("com.github.MojoLauncher:alsoft-android-aar:f369161d5f")

    api(project(":glfw:jni_bindings"))
    api(project(":sdl:jni_bindings"))
}
