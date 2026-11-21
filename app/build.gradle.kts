// Build settings for the app itself.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Firebase needs app/google-services.json, which you download from the
// Firebase console. Until that file exists, build without Firebase so the
// app still compiles and runs in "this phone only" mode.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Version numbers. GitHub Actions passes its build number (1, 2, 3...) as
// -PbuildNumber, so each published build is newer than the last one.
// A build on your own computer without it is called "0.1-dev".
val versionBase = providers.gradleProperty("appVersionBase").get()
val buildNumber = providers.gradleProperty("buildNumber").orNull?.toIntOrNull()

android {
    namespace = "com.hybrid87sg.bindertracker"
    // The Android version we build against (37.2 = Android 17, minor update 2).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.hybrid87sg.bindertracker"
        minSdk = 26
        targetSdk = 37
        // versionCode is the number Android compares to decide what is newer.
        // versionName is the label people see, like "0.1.6".
        versionCode = buildNumber ?: 1
        versionName = if (buildNumber != null) "$versionBase.$buildNumber" else "$versionBase-dev"

        // Where the app checks for new versions (a small file next to the APK
        // on the repo's "Latest debug build" release).
        buildConfigField(
            "String",
            "UPDATE_INFO_URL",
            "\"https://github.com/hybrid87sg/pokemon-binder-tracker/releases/download/debug-latest/latest.json\"",
        )
    }

    // A fixed debug key, committed to the repo on purpose. Google sign-in
    // only trusts apps signed with a key whose SHA-1 is registered in
    // Firebase, so every build (on any computer or in GitHub Actions) must
    // use this same key. It is only for debug builds, never for a store.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/binder-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // lets the code read the version number and update URL
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Screenshot tests save their PNGs into app/build/outputs/roborazzi.
                it.systemProperty("roborazzi.test.record", "true")
            }
        }
    }
}

// Room saves a description of each database version in app/schemas/. That
// lets it upgrade people's existing databases safely when tables change.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Jetpack Compose: the toolkit that draws the screens.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    // Room: the on-phone database (SQLite) for binders.
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // CameraX: camera preview and taking photos of binder pages.
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.androidx.exifinterface)

    // Coil: loads card pictures from the internet.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Firebase sign-in with a Google account.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)

    // Screenshot tests (run with: ./gradlew testDebugUnitTest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)
}
