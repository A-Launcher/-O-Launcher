pluginManagement {
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
    }
}

rootProject.name = "O Launcher"
include(":app")

include(":mojo-forge-installer")
project(":mojo-forge-installer").projectDir = file("third_party/MojoLauncher/forge_installer")
