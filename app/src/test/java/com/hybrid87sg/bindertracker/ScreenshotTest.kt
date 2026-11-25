package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.ArtWaiting
import com.hybrid87sg.bindertracker.data.OfflineArtStatus
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
import com.hybrid87sg.bindertracker.data.ExpansionOption
import com.hybrid87sg.bindertracker.ui.screens.ExpansionPicker
import com.hybrid87sg.bindertracker.data.PageSummary
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import com.hybrid87sg.bindertracker.ui.screens.CardScreen
import com.hybrid87sg.bindertracker.ui.screens.IndexStatus
import com.hybrid87sg.bindertracker.ui.screens.PageScreen
import com.hybrid87sg.bindertracker.ui.screens.ScanScreen
import com.hybrid87sg.bindertracker.ui.screens.LiveScanScreen
import com.hybrid87sg.bindertracker.ui.screens.Ladder
import com.hybrid87sg.bindertracker.ui.screens.StepRow
import com.hybrid87sg.bindertracker.ui.screens.StepState
import com.hybrid87sg.bindertracker.ui.screens.SearchScreen
import com.hybrid87sg.bindertracker.ui.screens.AccountScreen
import com.hybrid87sg.bindertracker.ui.screens.PermissionsScreen
import com.hybrid87sg.bindertracker.update.UpdateInfo
import com.hybrid87sg.bindertracker.ui.screens.BinderScreen
import com.hybrid87sg.bindertracker.ui.screens.EditBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.MissingCardsScreen
import com.hybrid87sg.bindertracker.data.BinderPocket
import com.hybrid87sg.bindertracker.data.ChecklistData
import com.hybrid87sg.bindertracker.data.ChecklistProgress
import com.hybrid87sg.bindertracker.data.ExpansionCount
import com.hybrid87sg.bindertracker.ui.screens.NewBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.ShelfScreen
import com.hybrid87sg.bindertracker.ui.screens.SignInScreen
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.BinderTrackerTheme
import com.hybrid87sg.bindertracker.ui.theme.DarkPalette
import com.hybrid87sg.bindertracker.ui.theme.LightPalette
import com.hybrid87sg.bindertracker.ui.theme.ThemeMode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import com.hybrid87sg.bindertracker.scan.RingCodeReader
import com.hybrid87sg.bindertracker.ui.components.RingCodeImage
import com.hybrid87sg.bindertracker.ui.components.colorFromHex
import org.junit.Rule
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import com.hybrid87sg.bindertracker.ui.components.MainTab
import com.hybrid87sg.bindertracker.ui.components.MainTabBar
import com.hybrid87sg.bindertracker.ui.screens.MissingTabScreen
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
            onSave = { _, _, _, _, _ -> },
        )
    }

    // ---- Missing cards ----

    private val kanto = Binder(id = "k", ownerId = "t", name = "Kanto", colorHex = SpineColor.Navy.hex, columns = 3, rows = 3, followedSets = "base1,sv151")

    private fun checklistSample(): ChecklistData {
        val names = listOf("Alakazam", "Blastoise", "Chansey", "Charizard", "Clefairy", "Gyarados", "Hitmonchan", "Machamp", "Magneton", "Mewtwo", "Nidoking", "Ninetales")
        val base = names.mapIndexed { i, n ->
            Card(id = "base1-${i + 1}", localId = "${i + 1}", name = n, setId = "base1", setName = "Base Set", imageUrl = null, rarity = if (i < 6) "Rare Holo" else "Rare", setOfficial = 102)
        }
        val sv = (1..10).map {
            Card(id = "sv151-$it", localId = "$it", name = "Card $it", setId = "sv151", setName = "151", imageUrl = null, rarity = if (it < 6) "Common" else "Double rare", setOfficial = 8)
        }
        val owned = setOf("base1-1", "base1-2", "base1-3", "base1-5", "base1-6", "base1-7", "sv151-1", "sv151-3")
        val pockets = owned.mapIndexed { i, id -> BinderPocket("p${i / 9}", i / 9L, i % 9, id) } + BinderPocket("p0", 0, 8, null)
        return ChecklistData(kanto, base + sv, pockets, owned)
    }

    @Test fun missingCards() = shoot("11-missing-cards") {
        MissingCardsScreen(
            data = checklistSample(), expansions = emptyList(), suggestion = null, cardListMissing = false,
            onBack = {}, onSetCollecting = { _ -> }, onSetMaster = {}, onPutIn = { _, _, _ -> }, onScanInto = { _, _ -> }, onOpenCards = { _, _, _ -> },
        )
    }

    @Test fun missingCardsDark() = shoot("11b-missing-cards-dark", dark = true) {
        MissingCardsScreen(
            data = checklistSample(), expansions = emptyList(), suggestion = null, cardListMissing = false,
            onBack = {}, onSetCollecting = { _ -> }, onSetMaster = {}, onPutIn = { _, _, _ -> }, onScanInto = { _, _ -> }, onOpenCards = { _, _, _ -> },
        )
    }

    @Test fun missingCardsNotCollecting() = shoot("11c-missing-cards-none") {
        MissingCardsScreen(
            data = ChecklistData(sample[0], emptyList(), emptyList(), emptySet()), expansions = emptyList(),
            suggestion = ExpansionCount("base1", "Base Set", 12), cardListMissing = false,
            onBack = {}, onSetCollecting = { _ -> }, onSetMaster = {}, onPutIn = { _, _, _ -> }, onScanInto = { _, _ -> }, onOpenCards = { _, _, _ -> },
        )
    }

    @Test fun shelfWithMissingBadges() = shoot("2e-shelf-missing-badges") {
        ShelfScreen(
            binders = sample, onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = {}, onSearch = {},
            missing = mapOf(sample[0].id to ChecklistProgress(38, 102), sample[1].id to ChecklistProgress(0, 165)),
        )
    }

    @Test fun shelfTurnedRight() = shoot("2f-shelf-turned-right") {
        ShelfScreen(
            binders = sample, onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = null, onSearch = null,
            look = com.hybrid87sg.bindertracker.data.ShelfLook(25, coversOnRight = true), onEditShelf = {},
        )
    }

    @Test fun shelfTurnedLeft() = shoot("2g-shelf-turned-left") {
        ShelfScreen(
            binders = sample, onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = null, onSearch = null,
            look = com.hybrid87sg.bindertracker.data.ShelfLook(35, coversOnRight = false), onEditShelf = {},
        )
    }

    @Test fun editShelf() = shoot("2h-edit-shelf") {
        com.hybrid87sg.bindertracker.ui.screens.EditShelfSheet(
            look = com.hybrid87sg.bindertracker.data.ShelfLook(20, coversOnRight = true, led = com.hybrid87sg.bindertracker.data.ShelfLook.LedPresets[3].colours),
            onChange = {}, onDone = {}, showsLights = true,
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        )
    }

    @Test fun binderWithMissingStrip() = shoot("4c-binder-missing") {
        BinderScreen(
            binder = kanto, pages = emptyList(), onBack = {}, onDelete = {}, onAddPages = {}, onEdit = {}, onOpenPage = {},
            progress = ChecklistProgress(95, 267), collectingLabel = "Base Set + 151",
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
            offlineArt = OfflineArtStatus(binderSaved = 212, binderTotal = 218, missingSaved = 164, missingTotal = 431, bytes = 11_400_000),
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
            offlineArt = OfflineArtStatus(binderSaved = 218, binderTotal = 218, missingSaved = 164, missingTotal = 431, bytes = 11_400_000, waiting = ArtWaiting.Wifi),
        )
    }

    @Test fun shelfDark() = shoot("2c-shelf-dark", dark = true) {
        ShelfScreen(binders = sample, onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = {}, onSearch = {})
    }

    @Test fun shelfMovedBinders() = shoot("2d-shelf-moved-binders") {
        ShelfScreen(binders = sample, notice = "3 binders added to your account.", onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = {}, onSearch = {})
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

    private val sampleExpansions = listOf(
        ExpansionOption("me02", "Phantasmal Flames", "me", "Mega Evolution"),
        ExpansionOption("me01", "Mega Evolution", "me", "Mega Evolution"),
        ExpansionOption("sv08", "Surging Sparks", "sv", "Scarlet & Violet"),
        ExpansionOption("sv03.5", "151", "sv", "Scarlet & Violet"),
        ExpansionOption("sv02", "Paldea Evolved", "sv", "Scarlet & Violet"),
        ExpansionOption("swsh4", "Vivid Voltage", "swsh", "Sword & Shield"),
    )

    @Test fun searchWithExpansion() = shoot("7c-search-expansion") {
        SearchScreen(
            title = "Search cards", backLabel = "Shelf", query = "pikachu", onQueryChange = {},
            results = sampleCards.filter { it.setId == "sv03.5" }, searching = false, status = status,
            onBack = {}, onCardClick = {}, onRefreshIndex = {}, autoFocus = false,
            expansions = sampleExpansions, selectedSet = "sv03.5",
        )
    }

    @Test fun expansionPicker() = shoot("7d-expansion-picker") {
        Box(Modifier.background(AppColors.Surface)) {
            ExpansionPicker(sampleExpansions, selectedSeries = "sv", selectedSet = "", onPick = { _, _ -> })
        }
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

    @Test fun liveScan() = shoot("9b-live-scan") {
        LiveScanScreen(
            position = 4,
            ladder = Ladder(
                number = StepRow(StepState.NotFound, "can't read"),
                name = StepRow(StepState.Found, "Charizard ex · 6 cards"),
                moves = StepRow(StepState.Found, "6 → 3 cards"),
                artwork = StepRow(StepState.Checking, ""),
            ),
            status = "Comparing the artwork…", locked = false, added = null,
            autoNext = true, onAutoNextChange = {}, capturing = false,
            onBack = {}, onChooseFromList = {}, onTakePhoto = {},
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
                    0 -> PocketWithCard(i, false, "base1-58", "Pikachu", "58", "Base Set", null, "Common", "base1", "base")
                    1 -> PocketWithCard(i, false, "base1-4", "Charizard", "4", "Base Set", null, "Rare Holo", "base1", "base")
                    7 -> PocketWithCard(i, true, null, null, null, null, null)
                    else -> PocketWithCard(i, false, null, null, null, null, null)
                }
            },
            photo = photo,
            guide = GuideRect(90f / 900, 120f / 1200, 810f / 900, 1080f / 1200),
            onBack = {}, onChooseCard = {}, onViewCard = { _, _ -> }, onMarkEmpty = {}, onClear = {}, onDeletePage = {},
        )
    }

    // Empty pockets (2 and 7) show the card-back picture; checks it in dark mode.
    @Test fun pageDark() = shoot("10b-page-dark", dark = true) {
        PageScreen(
            binderName = "Vintage", pageNumber = 3, columns = 3, rows = 3,
            pockets = List(9) { i ->
                when (i) {
                    0 -> PocketWithCard(i, false, "base1-58", "Pikachu", "58", "Base Set", null)
                    2, 7 -> PocketWithCard(i, true, null, null, null, null, null)
                    else -> PocketWithCard(i, false, null, null, null, null, null)
                }
            },
            photo = null,
            guide = GuideRect(0.1f, 0.1f, 0.9f, 0.9f),
            onBack = {}, onChooseCard = {}, onViewCard = { _, _ -> }, onMarkEmpty = {}, onClear = {}, onDeletePage = {},
        )
    }

    /** The binder ring code as the Share screen draws it, read back by the camera reader. */
    @Test fun ringCodeReadsBack() {
        val code = "7Q4KM2PX"
        // Brown is the lightest binder colour, so it's the hardest to read.
        shoot("11-ring-code") {
            Box(Modifier.background(AppColors.Surface).padding(40.dp)) {
                RingCodeImage(code, colorFromHex(SpineColor.Brown.hex), Modifier.size(240.dp))
            }
        }
        val picture = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(picture.width * picture.height)
        picture.getPixels(pixels, 0, picture.width, 0, 0, picture.width, picture.height)
        val gray = IntArray(pixels.size) { i ->
            val c = pixels[i]
            ((c shr 16 and 255) * 299 + (c shr 8 and 255) * 587 + (c and 255) * 114) / 1000
        }
        assertEquals(code, RingCodeReader.read(gray, picture.width, picture.height))
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

    @Test fun shelfWithTabs() = shoot("7-shelf-tabs") {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                ShelfScreen(binders = sample, onOpenBinder = {}, onChangeColor = { _, _ -> }, onAddBinder = {}, onOpenAccount = null, onSearch = null, onJoin = {})
            }
            MainTabBar(current = MainTab.Binders, missing = 42, onSelect = {})
        }
    }

    @Test fun missingTabEmptyDark() = shoot("7c-missing-tab-empty-dark", dark = true) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { MissingTabScreen(binders = emptyList(), onOpenCard = { _, _ -> }, onOpenList = {}) }
            MainTabBar(current = MainTab.Missing, missing = 0, onSelect = {})
        }
    }
}
