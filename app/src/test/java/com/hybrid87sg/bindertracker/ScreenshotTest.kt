package com.hybrid87sg.bindertracker

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hybrid87sg.bindertracker.auth.AppUser
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.ui.screens.AccountScreen
import com.hybrid87sg.bindertracker.ui.screens.BinderScreen
import com.hybrid87sg.bindertracker.ui.screens.NewBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.ShelfScreen
import com.hybrid87sg.bindertracker.ui.screens.SignInScreen
import com.hybrid87sg.bindertracker.ui.theme.BinderTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws each screen on the computer (no phone needed) and saves a picture
 * of it to app/build/outputs/roborazzi/. Handy for checking layouts.
 * Run with: ./gradlew testDebugUnitTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val sample = listOf(
        Binder(ownerId = "t", name = "Vintage", colorHex = SpineColor.Red.hex, columns = 3, rows = 3),
        Binder(ownerId = "t", name = "Modern", colorHex = SpineColor.Navy.hex, columns = 3, rows = 4),
        Binder(ownerId = "t", name = "Favourites", colorHex = SpineColor.Green.hex, columns = 2, rows = 2),
        Binder(ownerId = "t", name = "Scarlet & Violet era", colorHex = SpineColor.Plum.hex, columns = 3, rows = 3),
    )

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { BinderTrackerTheme { content() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun signIn() = shoot("1-sign-in") {
        SignInScreen(firebaseReady = true, signingIn = false, error = null, onGoogleSignIn = {}, onUseLocalOnly = {})
    }

    @Test fun signInNotConfigured() = shoot("1b-sign-in-no-firebase") {
        SignInScreen(firebaseReady = false, signingIn = false, error = null, onGoogleSignIn = {}, onUseLocalOnly = {})
    }

    @Test fun shelf() = shoot("2-shelf") {
        ShelfScreen(binders = sample, onOpenBinder = {}, onAddBinder = {}, onOpenAccount = {})
    }

    @Test fun shelfEmpty() = shoot("2b-shelf-empty") {
        ShelfScreen(binders = emptyList(), onOpenBinder = {}, onAddBinder = {}, onOpenAccount = {})
    }

    @Test fun newBinder() = shoot("3-new-binder") {
        NewBinderScreen(onCancel = {}, onCreate = { _, _, _ -> })
    }

    @Test fun binder() = shoot("4-binder") {
        BinderScreen(binder = sample[0], onBack = {}, onDelete = {})
    }

    @Test fun account() = shoot("5-account") {
        AccountScreen(user = AppUser("u", "Ash Ketchum", "ash@example.com", isLocalOnly = false), onBack = {}, onSignOut = {})
    }
}
