package com.hybrid87sg.bindertracker

import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.hybrid87sg.bindertracker.auth.AppUser
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.ui.components.ColorPicker
import com.hybrid87sg.bindertracker.ui.components.UpdateDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.PageSummary
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import com.hybrid87sg.bindertracker.ui.screens.CardScreen
import com.hybrid87sg.bindertracker.ui.screens.IndexStatus
import com.hybrid87sg.bindertracker.ui.screens.PageScreen
import com.hybrid87sg.bindertracker.ui.screens.ScanScreen
import com.hybrid87sg.bindertracker.ui.screens.SearchScreen
import com.hybrid87sg.bindertracker.ui.screens.AccountScreen
import com.hybrid87sg.bindertracker.ui.screens.PermissionsScreen
import com.hybrid87sg.bindertracker.update.UpdateInfo
import com.hybrid87sg.bindertracker.ui.screens.BinderScreen
import com.hybrid87sg.bindertracker.ui.screens.EditBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.NewBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.ShelfScreen
import com.hybrid87sg.bindertracker.ui.screens.SignInScreen
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.BinderTrackerTheme
import com.hybrid87sg.bindertracker.ui.theme.DarkPalette
import com.hybrid87sg.bindertracker.ui.theme.LightPalette
import com.hybrid87sg.bindertracker.ui.theme.ThemeMode
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

    private fun shoot(name: String, dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        AppColors.palette = if (dark) DarkPalette else LightPalette
        compose.setContent { BinderTrackerTheme(dark = dark) { content() } }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun signIn() = shoot("1-sign-in") {
        SignInScreen(firebaseReady = true, signingIn = false, error = null, onGoogleSignIn = {}, onUseLocalOnly = {})
    }

    @Test fun signInNotConfigured() = shoot("1b-sign-in-no-firebase") {
        SignInScreen(firebaseReady = false, signingIn = false, error = null, onGoogleSignIn = {}, onUseLocalOnly = {})
    }

    @Test fun shelfEmpty() = shoot("2b-shelf-empty") {
        ShelfScreen(binders = emptyList(), onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = {}, onSearch = {})
    }

    @Test fun colorPicker() = shoot("2c-color-picker") {
        Box(Modifier.background(Color(0xFFF6F4EF))) {
            ColorPicker(binderName = "Vintage", sizeLabel = "3 × 3", initialHex = "#F2C230", onSave = {}, onCancel = {})
        }
    }

    @Test fun newBinder() = shoot("3-new-binder") {
        NewBinderScreen(onCancel = {}, onCreate = { _, _, _ -> })
    }

    // 3 × 4 binder with 2 pages; cards in pockets 9, 10 and 11 of page 1 and 10 of page 2.
    // Shown at its current size, so no warning yet.
    @Test fun editBinder() = shoot("3b-edit-binder") {
        EditBinderScreen(
            binder = sample[0].copy(columns = 3, rows = 4),
            pageCount = 2,
            filledPositions = listOf(0, 1, 9, 10, 11, 3, 10),
            onCancel = {},
            onSave = { _, _, _ -> },
        )
    }

    @Test fun binder() = shoot("4-binder") {
        BinderScreen(binder = sample[0], pages = emptyList(), onBack = {}, onDelete = {}, onAddPages = {}, onEdit = {}, onOpenPage = {})
    }

    @Test fun account() = shoot("5-account") {
        AccountScreen(
            user = AppUser("u", "Ash Ketchum", "ash@example.com", isLocalOnly = false),
            version = "0.1.7",
            updateMessage = "You have the latest version.",
            onCheckForUpdates = {},
            themeMode = ThemeMode.System,
            onThemeModeChange = {},
            onBack = {},
            onSignOut = {},
            backupMessage = "Backed up 2 binders with 5 pages.",
        )
    }

    @Test fun accountDark() = shoot("5b-account-dark", dark = true) {
        AccountScreen(
            user = AppUser("u", "Ash Ketchum", "ash@example.com", isLocalOnly = false),
            version = "0.1.7",
            updateMessage = null,
            onCheckForUpdates = {},
            themeMode = ThemeMode.Dark,
            onThemeModeChange = {},
            onBack = {},
            onSignOut = {},
        )
    }

    @Test fun shelfDark() = shoot("2c-shelf-dark", dark = true) {
        ShelfScreen(binders = sample, onOpenBinder = {}, onAddBinder = {}, onOpenAccount = {}, onSearch = {})
    }

    @Test fun shelfWithSearch() = shoot("2-shelf") {
        ShelfScreen(binders = sample, onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = {}, onSearch = {})
    }

    private val sampleCards = listOf(
        Card("base1-58", "58", "Pikachu", "base1", "Base Set", null),
        Card("sv03.5-025", "025", "Pikachu", "sv03.5", "151", null),
        Card("swsh4-44", "044", "Pikachu", "swsh4", "Vivid Voltage", null),
    )
    private val status = IndexStatus(count = 23912, lastUpdated = 1795250000000, downloading = false, error = null)

    @Test fun search() = shoot("7-search") {
        SearchScreen(
            title = "Search cards", backLabel = "Shelf", query = "pikachu", onQueryChange = {},
            results = sampleCards, searching = false, status = status,
            onBack = {}, onCardClick = {}, onRefreshIndex = {}, autoFocus = false,
        )
    }

    @Test fun searchDownloading() = shoot("7b-search-downloading") {
        SearchScreen(
            title = "Search cards", backLabel = "Shelf", query = "", onQueryChange = {},
            results = emptyList(), searching = false, status = status.copy(count = 0, downloading = true),
            onBack = {}, onCardClick = {}, onRefreshIndex = {}, autoFocus = false,
        )
    }

    @Test fun card() = shoot("8-card") {
        val details = Tcgdex.parseCardDetails(javaClass.getResource("/tcgdex/card-base1-4.json")!!.readText())
        CardScreen(Card("base1-4", "4", "Charizard", "base1", "Base Set", null), details, failed = false, onRetry = {}, onBack = {})
    }

    @Test fun binderWithPages() = shoot("4b-binder-pages") {
        BinderScreen(
            binder = sample[0],
            pages = listOf(
                PageSummary("p1", "b", null, 1, identified = 9, empty = 0),
                PageSummary("p2", "b", null, 2, identified = 5, empty = 1),
            ),
            onBack = {}, onDelete = {}, onAddPages = {}, onEdit = {}, onOpenPage = {},
        )
    }

    @Test fun scan() = shoot("9-scan") {
        ScanScreen(
            binder = sample[0], capturing = false, error = null, onBack = {}, onCapture = {},
            onPreviewPositioned = {}, onGuidePositioned = {},
            cameraPreview = { m -> Box(m.background(Color(0xFF3A3A38))) },
        )
    }

    @Test fun page() = shoot("10-page") {
        // A pretend photo: a grey table with a 3 x 3 grid of coloured "cards".
        val photo = ImageBitmap(900, 1200).also { img ->
            val canvas = androidx.compose.ui.graphics.Canvas(img)
            canvas.drawRect(0f, 0f, 900f, 1200f, Paint().apply { color = Color(0xFF6B6B66) })
            val colors = listOf(0xFFE8C547, 0xFFD9534F, 0xFF4A90D9, 0xFF5CB85C, 0xFFE8C547, 0xFF9B59B6, 0xFFF0AD4E, 0xFFDDDDDD, 0xFF4A90D9)
            for (i in 0 until 9) {
                val col = i % 3; val row = i / 3
                val x = 90f + col * 240f; val y = 120f + row * 320f
                canvas.drawRect(x + 10, y + 10, x + 230, y + 310, Paint().apply { color = Color(colors[i]) })
            }
        }
        PageScreen(
            binderName = "Vintage", pageNumber = 2, columns = 3, rows = 3,
            pockets = List(9) { i ->
                when (i) {
                    0 -> PocketWithCard(i, false, "base1-58", "Pikachu", "58", "Base Set", null)
                    1 -> PocketWithCard(i, false, "base1-4", "Charizard", "4", "Base Set", null)
                    7 -> PocketWithCard(i, true, null, null, null, null, null)
                    else -> PocketWithCard(i, false, null, null, null, null, null)
                }
            },
            photo = photo,
            guide = GuideRect(90f / 900, 120f / 1200, 810f / 900, 1080f / 1200),
            onBack = {}, onChooseCard = {}, onViewCard = {}, onMarkEmpty = {}, onClear = {}, onDeletePage = {},
        )
    }

    @Test fun permissions() = shoot("0-permissions") {
        PermissionsScreen(cameraGranted = true, installGranted = false, cameraBlocked = false, onAllowCamera = {}, onAllowInstall = {})
    }

    @Test fun updateDialog() = shoot("6-update-dialog") {
        ShelfScreen(binders = sample, onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = {}, onSearch = {})
        UpdateDialog(
            update = UpdateInfo(8, "0.1.8", "https://example.com/app.apk"),
            currentVersion = "0.1.7",
            progress = 0.4f,
            error = null,
            onUpdate = {},
            onLater = {},
        )
    }
}
