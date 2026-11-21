package com.hybrid87sg.bindertracker

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hybrid87sg.bindertracker.auth.AuthManager
import com.hybrid87sg.bindertracker.backup.BackupFile
import com.hybrid87sg.bindertracker.backup.BinderBackup
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderSize
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.CardIndex
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.data.PageSummary
import com.hybrid87sg.bindertracker.data.Pocket
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.scan.PageReader
import com.hybrid87sg.bindertracker.scan.PocketMatcher
import com.hybrid87sg.bindertracker.tcgdex.CardDetails
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import java.io.File
import com.hybrid87sg.bindertracker.data.SpineColor
import com.hybrid87sg.bindertracker.update.UpdateInfo
import com.hybrid87sg.bindertracker.update.Updater
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private val db = AppDatabase.get(app)
    private val binderDao = db.binderDao()
    private val pageDao = db.pageDao()
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

    fun binder(id: String): Flow<Binder?> = binderDao.observeBinder(id)

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
        viewModelScope.launch {
            // Remove the binder's pages and their photos too.
            pageDao.pagesOf(binder.id).forEach { page ->
                page.photoPath?.let { File(it).delete() }
                pageDao.deletePage(page.id)
            }
            binderDao.delete(binder)
        }
    }

    // ---- Card index (checklist #7) ----

    val cardIndex = CardIndex(app, db.cardDao())

    /** Number of cards in the index on this phone (0 = not downloaded yet). */
    val cardCount: StateFlow<Int> = cardIndex.countFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _indexDownloading = MutableStateFlow(false)
    val indexDownloading: StateFlow<Boolean> = _indexDownloading.asStateFlow()

    private val _indexError = MutableStateFlow<String?>(null)
    val indexError: StateFlow<String?> = _indexError.asStateFlow()

    /** Downloads the card list if it's missing or a week old. [force] = always. */
    fun refreshCardIndex(force: Boolean = false) {
        if (_indexDownloading.value) return
        viewModelScope.launch {
            if (!force && !cardIndex.needsRefresh()) return@launch
            _indexDownloading.value = true
            _indexError.value = null
            runCatching { cardIndex.refresh() }
                .onFailure { _indexError.value = "Couldn't download the card list. Check your internet connection and try again." }
            _indexDownloading.value = false
        }
    }

    suspend fun searchCards(query: String): List<Card> = cardIndex.search(query)

    suspend fun card(id: String): Card? = cardIndex.get(id)

    /** Card details and market prices, fetched live from TCGdex. */
    suspend fun cardDetails(id: String): Result<CardDetails> = runCatching { Tcgdex.cardDetails(id) }

    // ---- Binder pages and pockets (checklist #4, #36) ----

    fun pages(binderId: String): Flow<List<PageSummary>> = pageDao.observePages(binderId)

    fun page(pageId: String): Flow<Page?> = pageDao.observePage(pageId)

    fun pockets(pageId: String): Flow<List<PocketWithCard>> = pageDao.observePockets(pageId)

    suspend fun pageNumber(page: Page): Int = pageDao.pageNumber(page.binderId, page.createdAt)

    /** Saves a new page from a photo and returns its id. */
    suspend fun createPage(binder: Binder, photo: File, guide: GuideRect): String {
        val page = Page(
            binderId = binder.id,
            photoPath = photo.absolutePath,
            guideLeft = guide.left,
            guideTop = guide.top,
            guideRight = guide.right,
            guideBottom = guide.bottom,
        )
        pageDao.createPage(page, binder.pocketsPerPage)
        readCards(page.id, binder, photo, guide)
        return page.id
    }

    /**
     * What automatic card reading is doing, per page: "Reading the cards…"
     * while it works, then a short result. The page screen shows it.
     */
    private val _readingStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val readingStatus: StateFlow<Map<String, String>> = _readingStatus.asStateFlow()

    private fun setReadingStatus(pageId: String, text: String) {
        _readingStatus.value = _readingStatus.value + (pageId to text)
    }

    /**
     * Reads the cards in a new page photo and fills in the pockets it's sure
     * about. Runs in the background, so the page opens straight away and
     * pockets fill in a moment later.
     */
    private fun readCards(pageId: String, binder: Binder, photo: File, guide: GuideRect) {
        setReadingStatus(pageId, "Reading the cards…")
        viewModelScope.launch {
            val result = runCatching {
                if (cardCount.value == 0) return@runCatching "Download the card list (search on the shelf) so pockets can fill in automatically."
                val read = PageReader.read(photo.absolutePath, guide, binder.columns, binder.rows)
                var found = 0
                for (card in read) {
                    val number = card.number ?: continue
                    val candidates = db.cardDao().allWithNumber(number)
                    val match = PocketMatcher.pick(card, candidates) ?: continue
                    found += pageDao.fillPocketIfUnset(pageId, card.position, match.id)
                }
                val total = binder.pocketsPerPage
                when (found) {
                    0 -> "Couldn't recognise any cards automatically. Tap a pocket to choose its card."
                    total -> "Recognised all $total cards. Tap one to check or change it."
                    else -> "Recognised $found of $total cards. Tap the others to choose them."
                }
            }
            setReadingStatus(
                pageId,
                result.getOrElse { "Couldn't read the cards automatically. Tap a pocket to choose its card." },
            )
        }
    }

    fun setPocketCard(pageId: String, position: Int, cardId: String) {
        viewModelScope.launch { pageDao.upsertPocket(Pocket(pageId, position, cardId = cardId)) }
    }

    fun setPocketEmpty(pageId: String, position: Int) {
        viewModelScope.launch { pageDao.upsertPocket(Pocket(pageId, position, isEmpty = true)) }
    }

    fun clearPocket(pageId: String, position: Int) {
        viewModelScope.launch { pageDao.upsertPocket(Pocket(pageId, position)) }
    }

    // ---- Backups ----

    private val backup = BinderBackup(binderDao, pageDao, File(app.filesDir, "pages"))

    /** True while a backup is being saved or restored. */
    private val _backupBusy = MutableStateFlow(false)
    val backupBusy: StateFlow<Boolean> = _backupBusy.asStateFlow()

    /** The result of the last backup or restore, shown on the Account screen. */
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    /** Saves every binder into the file the person picked in Android's "Save to" screen. */
    fun backUpTo(uri: Uri) {
        val owner = user.value ?: return
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            _backupMessage.value = "Saving your backup…"
            _backupMessage.value = runCatching {
                val out = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("Couldn't open the file")
                val result = out.use { backup.write(owner.uid, it) }
                "Backed up ${plural(result.binders, "binder")} with ${plural(result.pages, "page")}."
            }.getOrElse { "Couldn't save the backup. Try again, or pick a different place to save it." }
            _backupBusy.value = false
        }
    }

    /** Adds the binders from a backup file the person picked in Android's "Open" screen. */
    fun restoreFrom(uri: Uri) {
        val owner = user.value ?: return
        if (_backupBusy.value) return
        viewModelScope.launch {
            _backupBusy.value = true
            _backupMessage.value = "Restoring your backup…"
            _backupMessage.value = runCatching {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: error("Couldn't open the file")
                val result = input.use { backup.restore(owner.uid, it) }
                when {
                    result.binders == 0 && result.skipped > 0 -> "Everything in this backup is already on your shelf."
                    result.binders == 0 -> "This backup has no binders in it."
                    else -> "Restored ${plural(result.binders, "binder")} with ${plural(result.pages, "page")}." +
                        if (result.skipped > 0) " ${plural(result.skipped, "binder")} already on your shelf ${if (result.skipped == 1) "was" else "were"} left as ${if (result.skipped == 1) "it was" else "they were"}." else ""
                }
            }.getOrElse { e ->
                (e as? BackupFile.NotABackup)?.message ?: "Couldn't restore the backup. Try again."
            }
            _backupBusy.value = false
        }
    }

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

    fun deletePage(page: Page) {
        viewModelScope.launch {
            page.photoPath?.let { File(it).delete() }
            pageDao.deletePage(page.id)
        }
    }
}

/** Where the page sat in the photo, as fractions (0.0–1.0) of its width and height. */
data class GuideRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
