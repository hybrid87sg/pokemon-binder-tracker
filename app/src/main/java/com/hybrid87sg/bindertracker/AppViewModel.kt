package com.hybrid87sg.bindertracker

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hybrid87sg.bindertracker.auth.AuthManager
import com.hybrid87sg.bindertracker.cards.Card
import com.hybrid87sg.bindertracker.cards.CardIndex
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderSize
import com.hybrid87sg.bindertracker.data.PageWithPockets
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.update.UpdateInfo
import com.hybrid87sg.bindertracker.update.Updater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The "brain" behind the screens. Screens only draw things and report
 * taps; this class holds the data and does the work. It survives screen
 * rotation, so nothing is lost when the phone turns sideways.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val binderDao = AppDatabase.get(app).binderDao()
    private val pageDao = AppDatabase.get(app).pageDao()
    val auth = AuthManager(app)

    val user = auth.user

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    /** The signed-in person's binders. Switches automatically when the user changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val binders: StateFlow<List<Binder>> = user
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else binderDao.observeBinders(u.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- App updates ----

    private val updater = Updater(app)
    val currentVersion: String = updater.currentVersionName

    /** A newer version waiting to be installed, or null. Shows the update pop-up. */
    private val _update = MutableStateFlow<UpdateInfo?>(null)
    val update: StateFlow<UpdateInfo?> = _update.asStateFlow()

    /** Download progress 0.0–1.0 while downloading, otherwise null. */
    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    /** Result of pressing "Check for updates" on the Account screen. */
    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

    // The version the person said "Later" to, so we don't keep asking about it.
    private var postponedVersionCode: Int? = null

    /**
     * Asks GitHub whether a newer version exists. Runs every time the app
     * comes to the front. [manual] = the person pressed "Check for updates".
     */
    fun checkForUpdate(manual: Boolean = false) {
        if (_updateProgress.value != null) return // already downloading
        viewModelScope.launch {
            if (manual) _updateCheckMessage.value = "Checking…"
            val newer = runCatching { updater.findNewerVersion() }
            newer.onSuccess { info ->
                if (info != null && (manual || info.versionCode != postponedVersionCode)) {
                    _updateError.value = null
                    _update.value = info
                }
                if (manual) _updateCheckMessage.value = if (info == null) "You have the latest version." else null
            }
            newer.onFailure {
                if (manual) _updateCheckMessage.value = "Couldn't check for updates. Check your internet connection and try again."
            }
        }
    }

    /** Downloads the new version, then opens Android's install screen. */
    fun installUpdate() {
        val info = _update.value ?: return
        viewModelScope.launch {
            _updateError.value = null
            _updateProgress.value = 0f
            try {
                val file = updater.download(info) { _updateProgress.value = it }
                _updateProgress.value = null
                _update.value = null
                updater.install(file)
            } catch (e: Exception) {
                _updateProgress.value = null
                _updateError.value = "Download failed. Check your internet connection and try again."
            }
        }
    }

    fun postponeUpdate() {
        postponedVersionCode = _update.value?.versionCode
        _update.value = null
        _updateError.value = null
    }

    // ---- Card index and search ----

    /** Every Pokémon card, stored on the phone. Shared with the camera scanner. */
    val cardIndex = CardIndex.get(app)

    /** How many cards are on the phone (0 = not downloaded yet). */
    val cardCount: StateFlow<Int> = cardIndex.cardCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** When the card list was last downloaded, or null if never. */
    private val _cardsUpdatedAt = MutableStateFlow(cardIndex.lastUpdated)
    val cardsUpdatedAt: StateFlow<Long?> = _cardsUpdatedAt.asStateFlow()

    /** Download progress 0.0–1.0 while the card list downloads, otherwise null. */
    private val _cardDownloadProgress = MutableStateFlow<Float?>(null)
    val cardDownloadProgress: StateFlow<Float?> = _cardDownloadProgress.asStateFlow()

    private val _cardDownloadError = MutableStateFlow<String?>(null)
    val cardDownloadError: StateFlow<String?> = _cardDownloadError.asStateFlow()

    /** What's typed in the search box. */
    private val _cardQuery = MutableStateFlow("")
    val cardQuery: StateFlow<String> = _cardQuery.asStateFlow()

    /**
     * Search results. "debounce" waits until typing pauses for a moment, so
     * we search once per word instead of once per letter. Results also
     * refresh when the card list changes (e.g. right after downloading).
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val cardResults: StateFlow<List<Card>> = combine(_cardQuery.debounce(250), cardIndex.cardCount) { q, _ -> q }
        .mapLatest { q -> if (q.isBlank()) emptyList() else cardIndex.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCardQuery(text: String) {
        _cardQuery.value = text
    }

    /** Downloads (or refreshes) the card list from TCGdex. */
    fun downloadCards() {
        if (_cardDownloadProgress.value != null) return // already downloading
        viewModelScope.launch {
            _cardDownloadError.value = null
            _cardDownloadProgress.value = 0f
            try {
                cardIndex.download { _cardDownloadProgress.value = it }
                _cardsUpdatedAt.value = cardIndex.lastUpdated
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _cardDownloadError.value = "Couldn't download the card list. Check your internet connection and try again."
            } finally {
                _cardDownloadProgress.value = null
            }
        }
    }

    fun binder(id: String): Flow<Binder?> = binderDao.observeBinder(id)

    /** A binder's scanned pages, each with its pockets. */
    fun pages(binderId: String): Flow<List<PageWithPockets>> = pageDao.observePages(binderId)

    fun signIn(activityContext: Context) {
        viewModelScope.launch {
            _signingIn.value = true
            _signInError.value = auth.signInWithGoogle(activityContext)
            _signingIn.value = false
        }
    }

    fun useLocalOnly() = auth.useLocalOnly()

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    fun createBinder(name: String, color: SpineColor, size: BinderSize) {
        val owner = user.value ?: return
        viewModelScope.launch {
            binderDao.insert(
                Binder(
                    ownerId = owner.uid,
                    name = name.trim(),
                    colorHex = color.hex,
                    columns = size.columns,
                    rows = size.rows,
                ),
            )
        }
    }

    fun deleteBinder(binder: Binder) {
        viewModelScope.launch { binderDao.delete(binder) }
    }
}
