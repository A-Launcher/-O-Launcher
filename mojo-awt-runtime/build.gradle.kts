plugins {
    id("com.android.library")
}

android {
    namespace = "git.artdeell.mojo"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").apply {
        java.srcDir("../third_party/MojoLauncher/app_mojo/src/main/java")
        java.exclude("git/artdeell/mojo/ExitActivity.java")
        res.srcDir("../third_party/MojoLauncher/app_mojo/src/main/res")
        assets.srcDir("../third_party/MojoLauncher/app_mojo/src/main/assets")
        manifest.srcFile("src/main/AndroidManifest.xml")
    }
}

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
    api("com.google.code.gson:gson:2.13.1")
    api("org.tukaani:xz:1.10")
    api("net.sourceforge.htmlcleaner:htmlcleaner:2.29")

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
