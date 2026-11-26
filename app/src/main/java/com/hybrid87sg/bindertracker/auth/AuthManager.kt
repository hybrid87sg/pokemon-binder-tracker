package com.hybrid87sg.bindertracker.auth

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Who is using the app right now. */
data class AppUser(
    val uid: String,
    val name: String?,
    val email: String?,
    // True when using the app without signing in (this phone only).
    val isLocalOnly: Boolean,
)

/**
 * Handles signing in and out.
 *
 * How Google sign-in works, step by step:
 * 1. Android's Credential Manager shows the "choose a Google account" sheet.
 * 2. Google gives us an "ID token", a signed note that says who you are.
 * 3. We hand that token to Firebase Auth, which creates or finds your
 *    Firebase account. Its user ID is what your binders are saved under.
 */
class AuthManager(private val context: Context) {

    /**
     * Firebase only starts if app/google-services.json was present at build
     * time. Without it, we offer "this phone only" mode instead.
     */
    val isFirebaseConfigured: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val credentialManager = CredentialManager.create(context)

    private val _user = MutableStateFlow(loadCurrentUser())
    val user: StateFlow<AppUser?> = _user.asStateFlow()

    init {
        if (isFirebaseConfigured) {
            // Keeps "user" up to date if Firebase signs us in or out by itself.
            FirebaseAuth.getInstance().addAuthStateListener {
                _user.value = loadCurrentUser()
            }
        }
    }

    private fun loadCurrentUser(): AppUser? {
        if (isFirebaseConfigured) {
            FirebaseAuth.getInstance().currentUser?.let {
                return AppUser(it.uid, it.displayName, it.email, isLocalOnly = false)
            }
        }
        return if (prefs.getBoolean(KEY_LOCAL_MODE, false)) LOCAL_USER else null
    }

    /**
     * Shows Google's account picker and signs in to Firebase.
     * [activityContext] must be the screen's Activity so the picker can appear on it.
     * Returns null on success, or a friendly message to show if something failed.
     */
    suspend fun signInWithGoogle(activityContext: Context): String? {
        if (!isFirebaseConfigured) return "Google sign-in isn't connected yet."

        // The "web client ID" is created by Firebase and placed into the app
        // by google-services.json. We look it up by name so the app still
        // compiles before that file exists.
        @SuppressLint("DiscouragedApi") // on purpose: see the comment above
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return "Google sign-in is missing its client ID. Turn on Google sign-in in Firebase, then download google-services.json again."
        val webClientId = context.getString(resId)

        return try {
            val googleOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false) // show every Google account on the phone
                .setServerClientId(webClientId)
                .build()
            val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
            val result = credentialManager.getCredential(activityContext, request)

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                prefs.edit { putBoolean(KEY_LOCAL_MODE, false) }
                _user.value = loadCurrentUser()
                null
            } else {
                "Google didn't return an account. Try again."
            }
        } catch (e: GetCredentialCancellationException) {
            null // the person closed the account picker; nothing to report
        } catch (e: NoCredentialException) {
            // Android gives this same error when the phone has no Google account
            // AND when Google doesn't recognise this app yet (its SHA-1
            // fingerprint isn't registered in Firebase), so mention both.
            "Google sign-in couldn't start. Check that a Google account is added in your phone's " +
                "Settings. If one is, the app isn't registered with Google yet: use \"Use on this phone only\" for now."
        } catch (e: Exception) {
            "Couldn't sign in: ${e.message ?: "unknown problem"}. Check your internet connection and try again."
        }
    }

    /** Use the app without an account. Binders stay on this phone only. */
    fun useLocalOnly() {
        prefs.edit { putBoolean(KEY_LOCAL_MODE, true) }
        _user.value = LOCAL_USER
    }

    suspend fun signOut() {
        prefs.edit { putBoolean(KEY_LOCAL_MODE, false) }
        if (isFirebaseConfigured) {
            FirebaseAuth.getInstance().signOut()
            // Also forget the chosen account, so next time the picker shows again.
            runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        }
        _user.value = null
    }

    companion object {
        private const val KEY_LOCAL_MODE = "local_mode"
        val LOCAL_USER = AppUser(uid = "local", name = null, email = null, isLocalOnly = true)
    }
}
