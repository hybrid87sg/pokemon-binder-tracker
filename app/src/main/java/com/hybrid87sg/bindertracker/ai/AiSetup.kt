package com.hybrid87sg.bindertracker.ai

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Firebase AI Logic: the app's connection to Google's Gemini AI.
 *
 * Two parts:
 * 1. App Check. Every AI request carries a pass that says "this really is
 *    the Binder Tracker app". We use the debug kind of pass: the phone makes
 *    up a secret code once, and you paste that code into the Firebase console
 *    (App Check → Apps → ⋮ → Manage debug tokens) so Firebase trusts it.
 * 2. A test question to Gemini, so you can check everything is switched on.
 *    Real AI features (card recognition and so on) will build on [model].
 */
object AiSetup {
    private const val TAG = "AiSetup"

    /** The Gemini model we talk to. "Flash" models are quick and cheap (free tier is enough). */
    const val MODEL_NAME = "gemini-3.8-flash"

    // Where the App Check debug library keeps its secret code on the phone.
    private const val SECRET_PREFS = "com.google.firebase.appcheck.debug.store.%s"
    private const val SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

    /** False when the app was built without google-services.json. */
    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /**
     * Switches App Check on. Call once when the app starts, before anything
     * talks to Firebase. App Check only checks; it blocks nothing unless you
     * press "Enforce" in the console, so this can't break sign-in or sharing.
     */
    fun start(context: Context) {
        if (!isAvailable(context)) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }

    /**
     * The secret code to paste into the Firebase console. The library also
     * prints it in Logcat, but showing it in the app means no computer is needed.
     * Asking for a pass first makes sure the code has been created.
     */
    suspend fun debugCode(context: Context): String? = withContext(Dispatchers.IO) {
        if (!isAvailable(context)) return@withContext null
        // This fails until the code is registered, which is fine: the code exists either way.
        runCatching { FirebaseAppCheck.getInstance().getAppCheckToken(false).await() }
        val app = FirebaseApp.getInstance()
        context.getSharedPreferences(SECRET_PREFS.format(app.persistenceKey), Context.MODE_PRIVATE)
            .getString(SECRET_KEY, null)
    }

    /** The Gemini model, through Firebase (the free Gemini Developer API). */
    val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(MODEL_NAME)
    }

    /**
     * Asks Gemini one tiny question. Returns its answer, or a plain-English
     * hint about which setup step is missing.
     */
    suspend fun test(context: Context): Result<String> {
        if (!isAvailable(context)) return Result.failure(IllegalStateException("Firebase isn't connected in this build."))
        return runCatching {
            model.generateContent("In one short, cheerful sentence, say hello to a Pokémon card collector.")
                .text?.trim().orEmpty().ifEmpty { "Gemini answered, but with no words." }
        }.recoverCatching { e ->
            Log.w(TAG, "AI test failed", e)
            throw IllegalStateException(explain(e), e)
        }
    }

    /** A plain-English reason an AI request failed, pointing at the setup step that fixes it. */
    fun explain(e: Throwable): String {
        val msg = (e.message ?: "") + " " + (e.cause?.message ?: "")
        return when {
            "App Check" in msg || "app check" in msg.lowercase() || "attestation" in msg.lowercase() ->
                "Firebase doesn't trust this phone yet. Copy the code above into App Check → Manage debug tokens."
            "API has not been used" in msg || "SERVICE_DISABLED" in msg || "not enabled" in msg.lowercase() ->
                "AI Logic isn't switched on yet. In the Firebase console open AI Logic and press Get started."
            "quota" in msg.lowercase() || "429" in msg ->
                "The free AI allowance is used up for now. Try again later."
            "network" in msg.lowercase() || "Unable to resolve host" in msg ->
                "No internet connection."
            else -> "Something went wrong: ${e.message ?: e::class.simpleName}"
        }
    }
}
