// settings.gradle.kts: tells Gradle (the build tool) where to download
// plugins and libraries from, and which modules make up the project.
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

rootProject.name = "pokemon-binder-tracker"
include(":app")
