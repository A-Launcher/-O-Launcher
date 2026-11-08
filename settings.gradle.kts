pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.library" && requested.version == "8.11.1") {
                useVersion("8.13.2")
            }
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "O Launcher"
include(":app")
include(":mojo-awt-runtime")

include(":glfw:jni_bindings")
project(":glfw").projectDir = file("third_party/MojoLauncher/glfw")
project(":glfw:jni_bindings").projectDir = file("third_party/MojoLauncher/glfw/jni_bindings")
include(":mojoexec:jni_bindings")
project(":mojoexec").projectDir = file("third_party/MojoLauncher/mojoexec")
project(":mojoexec:jni_bindings").projectDir = file("third_party/MojoLauncher/mojoexec/jni_bindings")
include(":sdl:jni_bindings")
project(":sdl").projectDir = file("third_party/MojoLauncher/sdl")
project(":sdl:jni_bindings").projectDir = file("third_party/MojoLauncher/sdl/jni_bindings")

include(":mojo-forge-installer")
project(":mojo-forge-installer").projectDir = file("third_party/MojoLauncher/forge_installer")
