package com.hybrid87sg.bindertracker

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.ui.components.rememberPagePhoto
import com.hybrid87sg.bindertracker.ui.screens.CardRoute
import com.hybrid87sg.bindertracker.ui.screens.IndexStatus
import com.hybrid87sg.bindertracker.ui.screens.PageScreen
import com.hybrid87sg.bindertracker.ui.screens.ScanRoute
import com.hybrid87sg.bindertracker.ui.screens.SearchRoute
import kotlinx.coroutines.launch
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
import com.hybrid87sg.bindertracker.ui.screens.NewBinderScreen
import com.hybrid87sg.bindertracker.ui.screens.ShelfScreen
import com.hybrid87sg.bindertracker.ui.screens.SignInScreen
import com.hybrid87sg.bindertracker.ui.theme.BinderTrackerTheme

/** The app's single Activity (window). Every screen is drawn inside it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // draw behind the status bar, like modern apps do
        setContent {
            BinderTrackerTheme { App(activity = this) }
        }
    }
}

@Composable
private fun App(activity: ComponentActivity, vm: AppViewModel = viewModel()) {
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
    NavHost(navController = nav, startDestination = "shelf") {
        composable("shelf") {
            val binders by vm.binders.collectAsStateWithLifecycle()
            ShelfScreen(
                binders = binders,
                onOpenBinder = { nav.navigate("binder/${it.id}") },
                onAddBinder = { nav.navigate("new") },
                onOpenAccount = { nav.navigate("account") },
                onSearch = { nav.navigate("search") },
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
            binder?.let { b ->
                BinderScreen(
                    binder = b,
                    pages = pages,
                    onBack = { nav.popBackStack() },
                    onDelete = {
                        vm.deleteBinder(b)
                        nav.popBackStack()
                    },
                    onScan = { nav.navigate("scan/${b.id}") },
                    onOpenPage = { nav.navigate("page/${it.id}") },
                )
            }
        }

        // ---- Card index ----
        composable("search") {
            SearchRoute(
                title = "Search cards",
                backLabel = "Shelf",
                status = indexStatus(vm),
                search = vm::searchCards,
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

        // ---- Scanning a page ----
        composable("scan/{binderId}") { entry ->
            val binderId = entry.arguments?.getString("binderId").orEmpty()
            val binder by remember(binderId) { vm.binder(binderId) }.collectAsStateWithLifecycle(initialValue = null)
            val scope = rememberCoroutineScope()
            binder?.let { b ->
                ScanRoute(
                    binder = b,
                    onBack = { nav.popBackStack() },
                    onCaptured = { photo, guide ->
                        scope.launch {
                            val pageId = vm.createPage(b, photo, guide)
                            // Swap the camera for the new page, so "back" returns to the binder.
                            nav.navigate("page/$pageId") { popUpTo("scan/$binderId") { inclusive = true } }
                        }
                    },
                )
            }
        }
        composable("page/{pageId}") { entry ->
            val pageId = entry.arguments?.getString("pageId").orEmpty()
            val page by remember(pageId) { vm.page(pageId) }.collectAsStateWithLifecycle(initialValue = null)
            val pockets by remember(pageId) { vm.pockets(pageId) }.collectAsStateWithLifecycle(initialValue = emptyList())
            page?.let { p ->
                val binder by remember(p.binderId) { vm.binder(p.binderId) }.collectAsStateWithLifecycle(initialValue = null)
                val number by produceState(0, p.id) { value = vm.pageNumber(p) }
                val photo by rememberPagePhoto(p.photoPath)
                val reading by vm.readingStatus.collectAsStateWithLifecycle()
                binder?.let { b ->
                    PageScreen(
                        binderName = b.name,
                        pageNumber = number,
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
                        onRescan = { nav.navigate("rescan/$pageId") },
                        onScanCard = { position -> nav.navigate("scancard/$pageId/$position") },
                    )
                }
            }
        }
        // Take a new photo of an existing page. Pockets update with any new
        // cards the camera reads; then it goes back to the page.
        composable("rescan/{pageId}") { entry ->
            val pageId = entry.arguments?.getString("pageId").orEmpty()
            val page by remember(pageId) { vm.page(pageId) }.collectAsStateWithLifecycle(initialValue = null)
            page?.let { p ->
                val binder by remember(p.binderId) { vm.binder(p.binderId) }.collectAsStateWithLifecycle(initialValue = null)
                binder?.let { b ->
                    ScanRoute(
                        binder = b,
                        onBack = { nav.popBackStack() },
                        onCaptured = { photo, guide ->
                            vm.rescanPage(p, b, photo, guide)
                            nav.popBackStack()
                        },
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
            SearchRoute(
                title = "Which card is in pocket ${position + 1}?",
                backLabel = "Page",
                status = indexStatus(vm),
                search = vm::searchCards,
                onBack = { nav.popBackStack() },
                onCardClick = { card ->
                    vm.setPocketCard(pageId, position, card.id)
                    nav.popBackStack()
                },
                onRefreshIndex = { vm.refreshCardIndex(force = true) },
            )
        }

        composable("account") {
            val updateMessage by vm.updateCheckMessage.collectAsStateWithLifecycle()
            AccountScreen(
                user = current,
                version = vm.currentVersion,
                updateMessage = updateMessage,
                onCheckForUpdates = { vm.checkForUpdate(manual = true) },
                onBack = { nav.popBackStack() },
                onSignOut = vm::signOut,
            )
        }
    }
}

/** The card list's status, for the search screen. */
@Composable
private fun indexStatus(vm: AppViewModel): IndexStatus {
    val count by vm.cardCount.collectAsStateWithLifecycle()
    val downloading by vm.indexDownloading.collectAsStateWithLifecycle()
    val error by vm.indexError.collectAsStateWithLifecycle()
    return IndexStatus(count, vm.cardIndex.lastUpdated, downloading, error)
}
