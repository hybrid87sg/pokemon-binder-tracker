package com.hybrid87sg.bindertracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

    // Not signed in? Show only the sign-in screen.
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
            binder?.let { b ->
                BinderScreen(
                    binder = b,
                    onBack = { nav.popBackStack() },
                    onDelete = {
                        vm.deleteBinder(b)
                        nav.popBackStack()
                    },
                )
            }
        }
        composable("account") {
            AccountScreen(user = current, onBack = { nav.popBackStack() }, onSignOut = vm::signOut)
        }
    }
}
