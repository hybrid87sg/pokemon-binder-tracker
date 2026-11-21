package com.hybrid87sg.bindertracker

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hybrid87sg.bindertracker.auth.AppUser
import com.hybrid87sg.bindertracker.cards.Card
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.ui.components.UpdateDialog
import com.hybrid87sg.bindertracker.ui.screens.AccountScreen
import com.hybrid87sg.bindertracker.ui.screens.PermissionsScreen
import com.hybrid87sg.bindertracker.update.UpdateInfo
import com.hybrid87sg.bindertracker.ui.screens.BinderScreen
import com.hybrid87sg.bindertracker.ui.screens.CardSearchScreen
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
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
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
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
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
        AccountScreen(
            user = AppUser("u", "Ash Ketchum", "ash@example.com", isLocalOnly = false),
            version = "0.1.7",
            updateMessage = "You have the latest version.",
            onCheckForUpdates = {},
            onBack = {},
            onSignOut = {},
        )
    }

    @Test fun permissions() = shoot("0-permissions") {
        PermissionsScreen(cameraGranted = true, installGranted = false, cameraBlocked = false, onAllowCamera = {}, onAllowInstall = {})
    }

    @Test fun updateDialog() = shoot("6-update-dialog") {
        ShelfScreen(binders = sample, onOpenBinder = {}, onAddBinder = {}, onOpenAccount = {})
        UpdateDialog(
            update = UpdateInfo(8, "0.1.8", "https://example.com/app.apk"),
            currentVersion = "0.1.7",
            progress = 0.4f,
            error = null,
            onUpdate = {},
            onLater = {},
        )
    }

    private val sampleCards = listOf(
        Card("swsh1-49", "Pikachu", "049", "swsh1", "Sword & Shield", 202, 3, imageBase = null),
        Card("sv03.5-025", "Pikachu", "025", "sv03.5", "151", 165, 7, imageBase = null),
        Card("base1-58", "Pikachu", "58", "base1", "Base Set", 102, 1, imageBase = null),
        Card("swsh4-44", "Pikachu VMAX", "044", "swsh4", "Vivid Voltage", 185, 4, imageBase = null),
    )

    @Test fun cardIndexEmpty() = shoot("7-card-index-empty") {
        CardSearchScreen(
            cardCount = 0, updatedAt = null, downloadProgress = null, downloadError = null,
            query = "", results = emptyList(), onQueryChange = {}, onDownload = {}, onBack = {},
        )
    }

    @Test fun cardIndexDownloading() = shoot("7b-card-index-downloading") {
        CardSearchScreen(
            cardCount = 0, updatedAt = null, downloadProgress = 0.45f, downloadError = null,
            query = "", results = emptyList(), onQueryChange = {}, onDownload = {}, onBack = {},
        )
    }

    @Test fun cardSearch() = shoot("8-card-search") {
        CardSearchScreen(
            cardCount = 19_874, updatedAt = 1_795_000_000_000, downloadProgress = null, downloadError = null,
            query = "pikachu", results = sampleCards, onQueryChange = {}, onDownload = {}, onBack = {},
        )
    }

    @Test fun cardSearchNoResults() = shoot("8b-card-search-none") {
        CardSearchScreen(
            cardCount = 19_874, updatedAt = 1_795_000_000_000, downloadProgress = null, downloadError = null,
            query = "pikachoo", results = emptyList(), onQueryChange = {}, onDownload = {}, onBack = {},
        )
    }
}
