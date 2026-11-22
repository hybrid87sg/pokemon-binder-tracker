package com.hybrid87sg.bindertracker

import android.net.Uri
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.IntOffset
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.ui.components.Motion
import com.hybrid87sg.bindertracker.ui.screens.CollectionStats
import androidx.compose.ui.graphics.asImageBitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.hybrid87sg.bindertracker.data.ExpansionCount
import com.hybrid87sg.bindertracker.data.ExpansionOption
import com.hybrid87sg.bindertracker.ui.screens.MissingCardsScreen
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.ui.components.rememberPagePhoto
import com.hybrid87sg.bindertracker.ui.screens.CardRoute
import com.hybrid87sg.bindertracker.ui.screens.ExpansionPreset
import com.hybrid87sg.bindertracker.ui.screens.IndexStatus
import com.hybrid87sg.bindertracker.ui.screens.PageScreen
import com.hybrid87sg.bindertracker.ui.screens.ScanRoute
import com.hybrid87sg.bindertracker.ui.screens.SearchRoute
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hybrid87sg.bindertracker.permissions.Permissions
import com.hybrid87sg.bindertracker.ui.components.UpdateDialog
import com.hybrid87sg.bindertracker.ui.screens.PermissionsGate
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hybrid87sg.bindertracker.ui.screens.AccountScreen
import com.hybrid87sg.bindertracker.ui.screens.BinderScreen
import com.hybrid87sg.bindertracker.ui.screens.EditBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.JoinScreen
import com.hybrid87sg.bindertracker.ui.screens.ShareScreen
import com.hybrid87sg.bindertracker.sync.InviteCodes
import android.content.Intent
import com.hybrid87sg.bindertracker.ui.screens.NewBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.ShelfScreen
import com.hybrid87sg.bindertracker.ui.screens.SignInScreen
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import com.hybrid87sg.bindertracker.ui.theme.BinderTrackerTheme
import com.hybrid87sg.bindertracker.ui.theme.DarkPalette
import com.hybrid87sg.bindertracker.ui.theme.LightPalette
import com.hybrid87sg.bindertracker.ui.theme.ThemeSetting

/** The app's single Activity (window). Every screen is drawn inside it. */
class MainActivity : ComponentActivity() {
    // A share code from a QR code scanned with the phone's own camera app
    // ("bindertracker://join/7Q4KM2PX"), waiting to open the Join screen.
    private var joinCode by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readJoinLink(intent)
    }

    private fun readJoinLink(intent: Intent?) {
        val link = intent?.data ?: return
        if (link.scheme == "bindertracker" && link.host == "join") {
            InviteCodes.parse(link.toString())?.let { joinCode = it }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readJoinLink(intent)
        // Light or dark: read the saved choice and pick the colours before
        // anything is drawn, so the first frame already has the right ones.
        ThemeSetting.load(this)
        val startDark = ThemeSetting.isDark(ThemeSetting.mode, ThemeSetting.phoneIsDark(this))
        AppColors.palette = if (startDark) DarkPalette else LightPalette
        drawBehindSystemBars(startDark)
        setContent {
            val dark = ThemeSetting.isDark(ThemeSetting.mode, isSystemInDarkTheme())
            // When the mode changes, flip the clock and battery icons too.
            LaunchedEffect(dark) { drawBehindSystemBars(dark) }
            BinderTrackerTheme(dark = dark) {
                App(activity = this, joinCode = joinCode, onJoinCodeUsed = { joinCode = null })
            }
        }
    }

    /**
     * Draws the app behind the status bar, like modern apps do, with dark
     * icons on the light theme and white icons on the dark theme.
     */
    private fun drawBehindSystemBars(dark: Boolean) {
        val clear = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(clear) else SystemBarStyle.light(clear, clear)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
private fun App(
    activity: ComponentActivity,
    joinCode: String? = null,
    onJoinCodeUsed: () -> Unit = {},
    vm: AppViewModel = viewModel(),
) {
    val user by vm.user.collectAsStateWithLifecycle()
    val signInError by vm.signInError.collectAsStateWithLifecycle()
    val signingIn by vm.signingIn.collectAsStateWithLifecycle()

    val update by vm.update.collectAsStateWithLifecycle()
    val updateProgress by vm.updateProgress.collectAsStateWithLifecycle()
    val updateError by vm.updateError.collectAsStateWithLifecycle()

    // Step 1: permissions. Checked again every time the app comes to the
    // front, e.g. after the person comes back from the Settings app.
    var cameraGranted by remember { mutableStateOf(Permissions.hasCamera(activity)) }
    var installGranted by remember { mutableStateOf(Permissions.canInstallUpdates(activity)) }
    LifecycleResumeEffect(Unit) {
        cameraGranted = Permissions.hasCamera(activity)
        installGranted = Permissions.canInstallUpdates(activity)
        vm.checkForUpdate() // also look for a new version each time
        onPauseOrDispose { }
    }
    if (!cameraGranted || !installGranted) {
        PermissionsGate(
            cameraGranted = cameraGranted,
            installGranted = installGranted,
            onCameraResult = { cameraGranted = Permissions.hasCamera(activity) },
        )
        return
    }

    // Step 2: if a newer version exists, offer it on top of whatever screen is showing.
    update?.let { info ->
        UpdateDialog(
            update = info,
            currentVersion = vm.currentVersion,
            progress = updateProgress,
            error = updateError,
            onUpdate = vm::installUpdate,
            onLater = vm::postponeUpdate,
        )
    }

    // Step 3: not signed in? Show only the sign-in screen.
    val current = user
    if (current == null) {
        SignInScreen(
            firebaseReady = vm.auth.isFirebaseConfigured,
            signingIn = signingIn,
            error = signInError,
            onGoogleSignIn = { vm.signIn(activity) },
            onUseLocalOnly = vm::useLocalOnly,
        )
        return
    }

    // Download the card list the first time (and refresh it weekly).
    LaunchedEffect(Unit) { vm.refreshCardIndex() }

    // Signed in: the NavHost swaps between screens. Each "route" is a screen's address.
    val nav = rememberNavController()
    // Opened from a binder's QR code: go to the Join screen with that code.
    LaunchedEffect(joinCode) {
        joinCode?.let {
            onJoinCodeUsed()
            nav.navigate("join/$it")
        }
    }
    val syncActive by vm.syncActive.collectAsStateWithLifecycle()
    // How many cards the card list has (changes when it's downloaded), so expansion lists reload.
    val cardCount by vm.cardCount.collectAsStateWithLifecycle()
    val syncProblem by vm.syncProblem.collectAsStateWithLifecycle()
    // Screens slide in from the right and back out to the right, with a fade.
    val slide = tween<IntOffset>(Motion.Medium, easing = Motion.Ease)
    val fade = tween<Float>(Motion.Medium, easing = Motion.Ease)
    NavHost(
        navController = nav,
        startDestination = "shelf",
        enterTransition = { slideInHorizontally(slide) { it / 3 } + fadeIn(fade) },
        exitTransition = { slideOutHorizontally(slide) { -it / 6 } + fadeOut(fade) },
        popEnterTransition = { slideInHorizontally(slide) { -it / 6 } + fadeIn(fade) },
        popExitTransition = { slideOutHorizontally(slide) { it / 3 } + fadeOut(fade) },
    ) {
        composable("shelf") {
            val binders by vm.binders.collectAsStateWithLifecycle()
            val fill by vm.binderFill.collectAsStateWithLifecycle()
            val movedNotice by vm.movedBindersNotice.collectAsStateWithLifecycle()
            val missing by vm.missingCounts.collectAsStateWithLifecycle()
            ShelfScreen(
                missing = missing,
                binders = binders,
                fill = fill,
                notice = movedNotice,
                onDismissNotice = vm::dismissMovedBindersNotice,
                onOpenBinder = { nav.navigate("binder/${it.id}") },
                onChangeColor = { binder, hex -> vm.setBinderColor(binder, hex) },
                onAddBinder = { nav.navigate("new") },
                onOpenAccount = { nav.navigate("account") },
                onSearch = { nav.navigate("search") },
                onJoin = if (current.isLocalOnly || !vm.auth.isFirebaseConfigured) null else { { nav.navigate("join") } },
            )
        }
        composable("new") {
            NewBinderScreen(
                onCancel = { nav.popBackStack() },
                onCreate = { name, color, size ->
                    vm.createBinder(name, color, size)
                    nav.popBackStack()
                },
            )
        }
        composable("binder/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val binder by remember(id) { vm.binder(id) }.collectAsStateWithLifecycle(initialValue = null)
            val pages by remember(id) { vm.pages(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
            val missing by vm.missingCounts.collectAsStateWithLifecycle()
            val expansions by produceState(emptyList<ExpansionOption>(), cardCount) { value = vm.expansionList() }
            binder?.let { b ->
                BinderScreen(
                    binder = b,
                    pages = pages,
                    progress = missing[b.id],
                    collectingLabel = b.followedSetIds.joinToString(" + ") { sid -> expansions.firstOrNull { it.id == sid }?.name ?: sid },
                    onOpenMissing = { nav.navigate("missing/${b.id}") },
                    onBack = { nav.popBackStack() },
                    onDelete = {
                        vm.deleteBinder(b)
                        nav.popBackStack()
                    },
                    onAddPages = { count -> vm.addPages(b, count) },
                    onEdit = { nav.navigate("edit/${b.id}") },
                    onOpenPage = { nav.navigate("page/${it.id}") },
                    onShare = { nav.navigate("share/${b.id}") },
                )
            }
        }

        // ---- Sharing ----
        composable("share/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val binder by remember(id) { vm.binder(id) }.collectAsStateWithLifecycle(initialValue = null)
            val sharing by vm.sharing.collectAsStateWithLifecycle()
            binder?.let { b ->
                ShareScreen(
                    binder = b,
                    sharing = sharing[b.id],
                    online = syncActive,
                    problem = syncProblem,
                    onBack = { nav.popBackStack() },
                    onCreateInvite = { role -> vm.createInvite(b.id, role) },
                    onSetRole = { uid, role -> vm.setMemberRole(b.id, uid, role) },
                    onRemove = { uid -> vm.removeMember(b.id, uid) },
                    onStopSharing = {
                        vm.stopSharing(b.id)
                        nav.popBackStack()
                    },
                )
            }
        }
        composable("join") { JoinRoute(vm, startCode = null, onDone = { nav.popBackStack() }) }
        composable("join/{code}") { entry ->
            JoinRoute(vm, startCode = entry.arguments?.getString("code"), onDone = { nav.popBackStack() })
        }

        // ---- Card index ----
        // Edit binder: change name, colour and size. Opened from the pencil on a binder.
        composable("edit/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val binder by remember(id) { vm.binder(id) }.collectAsStateWithLifecycle(initialValue = null)
            val pages by remember(id) { vm.pages(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
            val filled by remember(id) { vm.filledPositions(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
            val expansions by produceState(emptyList<ExpansionOption>(), cardCount) { value = vm.expansionList() }
            binder?.let { b ->
                EditBinderScreen(
                    binder = b,
                    pageCount = pages.size,
                    filledPositions = filled,
                    onCancel = { nav.popBackStack() },
                    expansions = expansions,
                    onSave = { name, colorHex, size, collecting, secret ->
                        vm.updateBinder(b, name, colorHex, size, collecting, secret)
                        nav.popBackStack()
                    },
                )
            }
        }
        // Missing cards: the checklist for a binder that collects an expansion.
        composable("missing/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val data by remember(id) { vm.checklist(id) }.collectAsStateWithLifecycle(initialValue = null)
            val expansions by produceState(emptyList<ExpansionOption>(), cardCount) { value = vm.expansionList() }
            val suggestion by produceState<ExpansionCount?>(null, id, cardCount) { value = vm.topExpansion(id) }
            data?.let { d ->
                MissingCardsScreen(
                    data = d,
                    expansions = expansions,
                    suggestion = suggestion,
                    cardListMissing = cardCount == 0,
                    onBack = { nav.popBackStack() },
                    onSetCollecting = { sets, secret -> vm.setCollecting(d.binder, sets, secret) },
                    onPutIn = { pageId, position, cardId -> vm.setPocketCard(pageId, position, cardId) },
                    onScanInto = { pageId, position -> nav.navigate("scancard/$pageId/$position") },
                    onViewCard = { cardId -> nav.navigate("card/${Uri.encode(cardId)}") },
                )
            }
        }
        composable("search") {
            SearchRoute(
                title = "Search cards",
                backLabel = "Shelf",
                status = indexStatus(vm),
                search = vm::searchCards,
                loadExpansions = vm::expansionList,
                onBack = { nav.popBackStack() },
                onCardClick = { nav.navigate("card/${Uri.encode(it.id)}") },
                onRefreshIndex = { vm.refreshCardIndex(force = true) },
            )
        }
        composable("card/{id}") { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            val card by produceState<Card?>(null, id) { value = vm.card(id) }
            card?.let { CardRoute(it, vm::cardDetails, onBack = { nav.popBackStack() }) }
        }

        // ---- Pages ----
        composable("page/{pageId}") { entry ->
            // Which page is showing. Turning a page changes this in place,
            // so the page-turn animation flows straight into the new page.
            var pageId by rememberSaveable { mutableStateOf(entry.arguments?.getString("pageId").orEmpty()) }
            // Keep showing the last page we had until the new one arrives. (Starting
            // again from "no page" after a turn removed the whole screen for a
            // moment, so every card was rebuilt and popped in again.)
            var page by remember { mutableStateOf<Page?>(null) }
            LaunchedEffect(pageId) { vm.page(pageId).collect { page = it } }
            val binderId = page?.binderId
            val siblings by remember(binderId) { binderId?.let { vm.pages(it) } ?: flowOf(emptyList()) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val index = siblings.indexOfFirst { it.id == pageId }
            val prevId = siblings.getOrNull(index - 1)?.takeIf { index > 0 }?.id
            val nextId = siblings.getOrNull(index + 1)?.takeIf { index >= 0 }?.id
            // Pockets for this page and its neighbours, kept ready so turning is instant.
            val pocketsById = remember { mutableStateMapOf<String, List<PocketWithCard>>() }
            listOfNotNull(prevId, pageId, nextId).forEach { id ->
                key(id) { LaunchedEffect(id) { vm.pockets(id).collect { pocketsById[id] = it } } }
            }
            val pockets = pocketsById[pageId].orEmpty()
            page?.let { p ->
                val binder by remember(p.binderId) { vm.binder(p.binderId) }.collectAsStateWithLifecycle(initialValue = null)
                val number by produceState(0, p.id) { value = vm.pageNumber(p) }
                val photo by rememberPagePhoto(p.photoPath)
                val reading by vm.readingStatus.collectAsStateWithLifecycle()
                val suggestions by remember(pageId) { vm.suggestions(pageId) }.collectAsStateWithLifecycle(initialValue = emptyMap())
                val openPocket by vm.openPocket.collectAsStateWithLifecycle()
                val scanPictures by remember(pageId) { vm.scanPictures(pageId).map { m -> m.mapValues { it.value.asImageBitmap() } } }
                    .collectAsStateWithLifecycle(initialValue = emptyMap())
                // Every page's pockets, for the "Move to another page" picker.
                val allPockets by remember(p.binderId) { vm.binderPockets(p.binderId) }
                    .collectAsStateWithLifecycle(initialValue = emptyMap())
                binder?.let { b ->
                    PageScreen(
                        binderName = b.name,
                        pageNumber = if (index >= 0) index + 1 else number,
                        columns = b.columns,
                        rows = b.rows,
                        pockets = pockets,
                        photo = photo,
                        guide = GuideRect(p.guideLeft, p.guideTop, p.guideRight, p.guideBottom),
                        readingStatus = reading[p.id],
                        onBack = { nav.popBackStack() },
                        onChooseCard = { position -> nav.navigate("pick/$pageId/$position") },
                        onViewCard = { cardId -> nav.navigate("card/${Uri.encode(cardId)}") },
                        onMarkEmpty = { position -> vm.setPocketEmpty(pageId, position) },
                        onClear = { position -> vm.clearPocket(pageId, position) },
                        onDeletePage = {
                            vm.deletePage(p)
                            nav.popBackStack()
                        },
                        onScanCard = { position -> nav.navigate("scancard/$pageId/$position") },
                        suggestions = suggestions,
                        onPickSuggestion = { position, cardId -> vm.setPocketCard(pageId, position, cardId) },
                        openPosition = openPocket?.takeIf { it.first == pageId }?.second,
                        onPocketOpened = vm::pocketOpened,
                        scanPictures = scanPictures,
                        pageId = pageId,
                        pageCount = siblings.size,
                        prevPageId = prevId,
                        nextPageId = nextId,
                        prevPockets = prevId?.let { pocketsById[it] },
                        nextPockets = nextId?.let { pocketsById[it] },
                        onTurn = { dir -> (if (dir > 0) nextId else prevId)?.let { pageId = it } },
                        canEdit = b.canEdit,
                        pageIds = siblings.map { it.id },
                        pageCards = allPockets,
                        onSwap = vm::swapPockets,
                        onMoveToNewPage = { position, onCreated -> vm.moveToNewPage(b, pageId, position, onCreated) },
                        onGoToPage = { pageId = it },
                    )
                }
            }
        }
        // Photograph one card for one pocket; then back to the page.
        composable("scancard/{pageId}/{position}") { entry ->
            val pageId = entry.arguments?.getString("pageId").orEmpty()
            val position = entry.arguments?.getString("position")?.toIntOrNull() ?: 0
            val page by remember(pageId) { vm.page(pageId) }.collectAsStateWithLifecycle(initialValue = null)
            page?.let { p ->
                val binder by remember(p.binderId) { vm.binder(p.binderId) }.collectAsStateWithLifecycle(initialValue = null)
                binder?.let { b ->
                    ScanRoute(
                        binder = b,
                        singleCard = true,
                        subtitle = "Pocket ${position + 1}",
                        onBack = { nav.popBackStack() },
                        onCaptured = { photo, guide ->
                            vm.scanPocket(pageId, position, photo, guide)
                            nav.popBackStack()
                        },
                    )
                }
            }
        }
        // Search, but tapping a card puts it in a pocket.
        composable("pick/{pageId}/{position}") { entry ->
            val pageId = entry.arguments?.getString("pageId").orEmpty()
            val position = entry.arguments?.getString("position")?.toIntOrNull() ?: 0
            // If the last cards came from one expansion, start the search on it.
            val preset by produceState<ExpansionPreset?>(null, pageId) {
                value = vm.expansionStreak(pageId)?.let { ExpansionPreset(it.setId, it.count) }
            }
            SearchRoute(
                title = "Which card is in pocket ${position + 1}?",
                backLabel = "Page",
                status = indexStatus(vm),
                search = vm::searchCards,
                loadExpansions = vm::expansionList,
                onBack = { nav.popBackStack() },
                preset = preset,
                onCardClick = { card ->
                    vm.setPocketCard(pageId, position, card.id)
                    nav.popBackStack()
                },
                onRefreshIndex = { vm.refreshCardIndex(force = true) },
            )
        }

        composable("account") {
            val updateMessage by vm.updateCheckMessage.collectAsStateWithLifecycle()
            val backupMessage by vm.backupMessage.collectAsStateWithLifecycle()
            val backupBusy by vm.backupBusy.collectAsStateWithLifecycle()
            val binders by vm.binders.collectAsStateWithLifecycle()
            val fill by vm.binderFill.collectAsStateWithLifecycle()
            AccountScreen(
                stats = CollectionStats(
                    cards = binders.sumOf { fill[it.id]?.filled ?: 0 },
                    pages = binders.sumOf { fill[it.id]?.pages ?: 0 },
                    binders = binders.size,
                ),
                user = current,
                version = vm.currentVersion,
                updateMessage = updateMessage,
                onCheckForUpdates = { vm.checkForUpdate(manual = true) },
                themeMode = ThemeSetting.mode,
                onThemeModeChange = { ThemeSetting.set(activity, it) },
                onBack = { nav.popBackStack() },
                onSignOut = vm::signOut,
                backupMessage = backupMessage,
                backupBusy = backupBusy,
                onBackUp = vm::backUpTo,
                onRestore = vm::restoreFrom,
                syncActive = syncActive,
                syncProblem = syncProblem,
            )
        }
    }
}

/** The Join screen, joined up to the app's data. */
@Composable
private fun JoinRoute(vm: AppViewModel, startCode: String?, onDone: () -> Unit) {
    val state by vm.joinState.collectAsStateWithLifecycle()
    JoinScreen(
        state = state,
        startCode = startCode,
        onBack = onDone,
        onLookUp = vm::lookUpInvite,
        onJoin = vm::joinBinder,
        onReset = vm::resetJoin,
        onJoined = {
            vm.resetJoin()
            onDone()
        },
    )
}

/** The card list's status, for the search screen. */
@Composable
private fun indexStatus(vm: AppViewModel): IndexStatus {
    val count by vm.cardCount.collectAsStateWithLifecycle()
    val downloading by vm.indexDownloading.collectAsStateWithLifecycle()
    val error by vm.indexError.collectAsStateWithLifecycle()
    return IndexStatus(count, vm.cardIndex.lastUpdated, downloading, error)
}
