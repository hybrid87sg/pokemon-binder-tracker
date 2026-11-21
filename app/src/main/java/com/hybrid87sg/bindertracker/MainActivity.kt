package com.hybrid87sg.bindertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.hybrid87sg.bindertracker.scan.ScanViewModel
import com.hybrid87sg.bindertracker.ui.screens.BinderScreen
import com.hybrid87sg.bindertracker.ui.screens.CardSearchScreen
import com.hybrid87sg.bindertracker.ui.screens.ScanScreen
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
                onOpenCards = { nav.navigate("cards") },
            )
        }
        composable("cards") {
            val cardCount by vm.cardCount.collectAsStateWithLifecycle()
            val updatedAt by vm.cardsUpdatedAt.collectAsStateWithLifecycle()
            val progress by vm.cardDownloadProgress.collectAsStateWithLifecycle()
            val error by vm.cardDownloadError.collectAsStateWithLifecycle()
            val query by vm.cardQuery.collectAsStateWithLifecycle()
            val results by vm.cardResults.collectAsStateWithLifecycle()
            CardSearchScreen(
                cardCount = cardCount,
                updatedAt = updatedAt,
                downloadProgress = progress,
                downloadError = error,
                query = query,
                results = results,
                onQueryChange = vm::setCardQuery,
                onDownload = vm::downloadCards,
                onBack = { nav.popBackStack() },
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
            val binderFlow = remember(id) { vm.binder(id) }
            val binder by binderFlow.collectAsStateWithLifecycle(initialValue = null)
            val pagesFlow = remember(id) { vm.pages(id) }
            val pages by pagesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
            binder?.let { b ->
                BinderScreen(
                    binder = b,
                    pages = pages,
                    onBack = { nav.popBackStack() },
                    onDelete = {
                        vm.deleteBinder(b)
                        nav.popBackStack()
                    },
                    onScan = { nav.navigate("binder/${b.id}/scan") },
                )
            }
        }
        composable("binder/{id}/scan") {
            // The scan screen has its own "brain" (ScanViewModel), created
            // fresh for each scan and thrown away when the screen closes.
            val scanVm: ScanViewModel = viewModel()
            val binder by scanVm.binder.collectAsStateWithLifecycle(initialValue = null)
            val step by scanVm.step.collectAsStateWithLifecycle()
            binder?.let { b ->
                ScanScreen(
                    binder = b,
                    step = step,
                    onBack = { nav.popBackStack() },
                    onPhotoTaken = { photo, rotation, guide -> scanVm.onPhotoTaken(photo, rotation, guide, b) },
                    onPhotoFailed = scanVm::onPhotoFailed,
                    onRetake = scanVm::retake,
                    onChoose = scanVm::choose,
                    onNameByHand = scanVm::nameByHand,
                    onSearch = scanVm::search,
                    onSave = { scanVm.save { nav.popBackStack() } },
                )
            }
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
