package com.hybrid87sg.bindertracker

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hybrid87sg.bindertracker.auth.AuthManager
import com.hybrid87sg.bindertracker.backup.BackupFile
import com.hybrid87sg.bindertracker.backup.BinderBackup
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderResize
import com.hybrid87sg.bindertracker.data.BinderSize
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.CardIndex
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.data.PageSummary
import com.hybrid87sg.bindertracker.data.BinderFill
import com.hybrid87sg.bindertracker.data.Pocket
import com.hybrid87sg.bindertracker.data.ExpansionOption
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.scan.CardRecognizer
import com.hybrid87sg.bindertracker.scan.DetailMatcher
import com.hybrid87sg.bindertracker.scan.ExpansionStreak
import com.hybrid87sg.bindertracker.scan.GuessPicker
import com.hybrid87sg.bindertracker.scan.PageReader
import com.hybrid87sg.bindertracker.scan.PocketMatcher
import com.hybrid87sg.bindertracker.scan.ReadCard
import com.hybrid87sg.bindertracker.tcgdex.CardDetails
import com.hybrid87sg.bindertracker.tcgdex.Tcgdex
import androidx.room.withTransaction
import java.io.File
import com.hybrid87sg.bindertracker.update.UpdateInfo
import com.hybrid87sg.bindertracker.update.Updater
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    /**
     * "3 binders added to your account." Shown on the shelf after signing in
     * if binders made in "phone only" mode were moved to the account.
     * Null when there is nothing to show.
     */
    private val _movedBindersNotice = MutableStateFlow<String?>(null)
    val movedBindersNotice: StateFlow<String?> = _movedBindersNotice.asStateFlow()

    fun dismissMovedBindersNotice() {
        _movedBindersNotice.value = null
    }

    init {
        // Whenever someone is signed in with Google, bring over any binders
        // made before signing in ("phone only" mode), so that work is never
        // hidden. This also rescues binders from before this update, for
        // people who already signed in and lost sight of them.
        viewModelScope.launch {
            user.collect { u ->
                if (u != null && !u.isLocalOnly) {
                    val moved = binderDao.moveBinders(fromOwner = AuthManager.LOCAL_USER.uid, toOwner = u.uid)
                    if (moved > 0) {
                        _movedBindersNotice.value =
                            if (moved == 1) "1 binder added to your account." else "$moved binders added to your account."
                    }
                }
            }
        }
    }

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

    fun createBinder(name: String, colorHex: String, size: BinderSize) {
        val owner = user.value ?: return
        viewModelScope.launch {
            binderDao.insert(
                Binder(
                    ownerId = owner.uid,
                    name = name.trim(),
                    colorHex = colorHex,
                    columns = size.columns,
                    rows = size.rows,
                ),
            )
        }
    }

    /** Repaints a binder's spine. [colorHex] is text like "#F2C230". */
    fun setBinderColor(binder: Binder, colorHex: String) {
        viewModelScope.launch { binderDao.updateColor(binder.id, colorHex) }
    }

    /** Pocket numbers holding a card in this binder (for the Edit binder warning). */
    fun filledPositions(binderId: String): Flow<List<Int>> = pageDao.observeFilledPositions(binderId)

    /**
     * Saves the Edit binder screen: new name, colour and size.
     *
     * Bigger size: every page gets the extra (empty) pockets.
     * Smaller size: cards in pockets that no longer exist move, in order,
     * onto new pages added at the back (see [BinderResize]).
     *
     * "withTransaction" means all of these changes happen together or not
     * at all, so a crash halfway can never lose a card.
     */
    fun updateBinder(binder: Binder, name: String, colorHex: String, size: BinderSize) {
        viewModelScope.launch {
            db.withTransaction {
                val oldCount = binder.pocketsPerPage
                val newCount = size.pockets
                val pages = pageDao.pagesOf(binder.id).sortedBy { it.createdAt }
                if (newCount > oldCount) {
                    pages.forEach { page ->
                        pageDao.insertPocketsIfMissing((oldCount until newCount).map { Pocket(page.id, it) })
                    }
                } else if (newCount < oldCount) {
                    val moved = BinderResize.cardsThatDontFit(pages.map { pageDao.pocketsOf(it.id) }, newCount)
                    pages.forEach { pageDao.deletePocketsFrom(it.id, newCount) }
                    // New pages go after the last existing page.
                    val start = maxOf(System.currentTimeMillis(), (pages.maxOfOrNull { it.createdAt } ?: 0L) + 1)
                    BinderResize.newPages(moved, newCount).forEachIndexed { i, cards ->
                        val page = Page(binderId = binder.id, photoPath = null, createdAt = start + i)
                        pageDao.createPage(page, newCount)
                        cards.forEachIndexed { position, cardId ->
                            pageDao.upsertPocket(Pocket(page.id, position, cardId = cardId))
                        }
                    }
                }
                binderDao.update(
                    binder.copy(name = name.trim(), colorHex = colorHex, columns = size.columns, rows = size.rows),
                )
            }
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

    suspend fun searchCards(query: String, seriesId: String = "", setId: String = ""): List<Card> =
        cardIndex.search(query, seriesId, setId)

    suspend fun expansionList(): List<ExpansionOption> = cardIndex.expansions()

    suspend fun card(id: String): Card? = cardIndex.get(id)

    /** Card details and market prices, fetched live from TCGdex. */
    suspend fun cardDetails(id: String): Result<CardDetails> = runCatching { Tcgdex.cardDetails(id) }

    // ---- Binder pages and pockets (checklist #4, #36) ----

    fun pages(binderId: String): Flow<List<PageSummary>> = pageDao.observePages(binderId)

    /** How full each binder is, by binder ID, for the shelf and the Account totals. */
    val binderFill: StateFlow<Map<String, BinderFill>> = pageDao.observeBinderFill()
        .map { list -> list.associateBy { it.binderId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun page(pageId: String): Flow<Page?> = pageDao.observePage(pageId)

    fun pockets(pageId: String): Flow<List<PocketWithCard>> = pageDao.observePockets(pageId)

    suspend fun pageNumber(page: Page): Int = pageDao.pageNumber(page.binderId, page.createdAt)

    /**
     * Adds [count] empty pages to the end of a binder. Each pocket is then
     * filled by scanning its card on its own (from the pocket's menu).
     */
    fun addPages(binder: Binder, count: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // A millisecond apart, so the pages keep their order.
            repeat(count) { i -> pageDao.createPage(Page(binderId = binder.id, photoPath = null, createdAt = now + i), binder.pocketsPerPage) }
        }
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

    private val recognizer = CardRecognizer.get(app)

    /**
     * Best guesses for pockets the app wasn't sure about, by page then pocket
     * position, so the pocket's menu can offer them to tap. Kept while the
     * app is open.
     */
    private val _suggestions = MutableStateFlow<Map<String, Map<Int, List<Card>>>>(emptyMap())

    fun suggestions(pageId: String): Flow<Map<Int, List<Card>>> = _suggestions.map { it[pageId].orEmpty() }

    private fun setSuggestions(pageId: String, position: Int, cards: List<Card>) {
        val page = _suggestions.value[pageId].orEmpty()
        val updated = if (cards.isEmpty()) page - position else page + (position to cards)
        _suggestions.value = _suggestions.value + (pageId to updated)
    }

    /**
     * The bit of the photo the app looked at for each pocket, so the
     * pocket's menu can show "what the camera saw". Kept while the app is open.
     */
    private val _scanPictures = MutableStateFlow<Map<String, Map<Int, Bitmap>>>(emptyMap())

    fun scanPictures(pageId: String): Flow<Map<Int, Bitmap>> = _scanPictures.map { it[pageId].orEmpty() }

    private fun setScanPicture(pageId: String, position: Int, picture: Bitmap?) {
        val page = _scanPictures.value[pageId].orEmpty()
        _scanPictures.value = _scanPictures.value + (pageId to if (picture == null) page - position else page + (position to picture))
    }

    /** A pocket whose menu should open by itself (after scanning one card it wasn't sure about). */
    private val _openPocket = MutableStateFlow<Pair<String, Int>?>(null)
    val openPocket: StateFlow<Pair<String, Int>?> = _openPocket.asStateFlow()

    fun pocketOpened() { _openPocket.value = null }

    /** Which expansion the last cards put in each binder came from (see [ExpansionStreak]). */
    private val streaks = ExpansionStreak()

    private suspend fun binderIdOf(pageId: String): String? = pageDao.observePage(pageId).first()?.binderId

    private suspend fun recordCard(pageId: String, card: Card) {
        binderIdOf(pageId)?.let { streaks.record(it, card.setId) }
    }

    /**
     * The expansion to preset in "Choose card from list" for this page: set when
     * the last two or more cards put in its binder came from the same expansion.
     */
    suspend fun expansionStreak(pageId: String): ExpansionStreak.Streak? = binderIdOf(pageId)?.let(streaks::current)

    /** What recognising one pocket found: the card it's sure of, and/or guesses to pick from. */
    private data class Recognition(val card: Card?, val fromPicture: Boolean, val guesses: List<Card>)

    /**
     * Makes sure the card pictures list (for recognising by picture) is on the
     * phone, downloading it the first time. Returns false if it isn't.
     */
    private suspend fun ensurePicturesList(pageId: String): Boolean {
        if (recognizer.isDownloaded) return true
        setReadingStatus(pageId, "Downloading the card pictures list (one time, about 20 MB)…")
        return recognizer.download()
    }

    /**
     * Works out which card is in one pocket.
     * 1. Name and number both read and both match a card: surest.
     * 2. Otherwise the AI picture model finds the cards that look most like
     *    it. One is used if the text read agrees with it, or if it's clearly
     *    ahead; the rest are kept as guesses to pick from.
     */
    private suspend fun recognise(card: ReadCard, picture: Bitmap?): Recognition {
        card.number?.let { PocketMatcher.pick(card, db.cardDao().allWithNumber(it)) }?.let { return Recognition(it, false, emptyList()) }
        if (picture == null) return Recognition(null, false, emptyList())
        // Find the card's corners and flatten it, so it lines up with official pictures.
        val flat = DetailMatcher.straighten(picture) ?: picture
        val (index, numbers) = recognizer.describe(flat) ?: return Recognition(null, false, emptyList())
        // 2. Shortlist about 20 look-alikes with the AI picture model: among
        //    cards with the name that was read if there is one, else all cards.
        val sameName = card.name?.let { PocketMatcher.sameName(it, db.cardDao().searchByName(it, limit = 400)) }.orEmpty()
        val ranked = if (sameName.isNotEmpty()) index.rank(numbers, sameName.map { it.id }) else emptyList()
        val shortlist = (ranked.ifEmpty { index.nearest(numbers, count = SHORTLIST) }).take(SHORTLIST)
            .mapNotNull { g -> db.cardDao().get(g.cardId) }
        // 3. Detail matching picks the exact card from the shortlist.
        val byDetails = DetailMatcher.pickByDetails(flat, shortlist.mapNotNull { c -> c.imageUrl?.let { c.id to it } })
        if (byDetails.isNotEmpty() && byDetails.first().matches > 0) {
            val byId = shortlist.associateBy { it.id }
            val chosen = DetailMatcher.confident(byDetails)?.let(byId::get)
            return Recognition(chosen, chosen != null, byDetails.take(5).mapNotNull { byId[it.cardId] })
        }
        // Detail matching couldn't run (no internet?): fall back to the picture model alone.
        if (ranked.isNotEmpty()) {
            val chosen = GuessPicker.chooseAmongSameName(ranked)?.let { id -> shortlist.firstOrNull { it.id == id } }
            return Recognition(chosen, chosen != null, shortlist.take(5))
        }
        val guesses = shortlist.take(5)
        val scored = index.rank(numbers, guesses.map { it.id }).mapNotNull { g -> guesses.firstOrNull { it.id == g.cardId }?.let { g to it } }
        val chosen = GuessPicker.choose(card, scored.map { (g, c) -> g to (c.name to c.number) })
        return Recognition(guesses.firstOrNull { it.id == chosen }, chosen != null, guesses)
    }

    /**
     * Scans one card on its own (from a pocket's menu) and puts it in that
     * pocket. A photo of a single card is much bigger and sharper than the
     * same card in a whole-page photo, so it's easier to recognise. If the
     * app isn't sure, the pocket's menu opens with its best guesses. The
     * photo is only used for recognising and is deleted afterwards.
     */
    fun scanPocket(pageId: String, position: Int, photo: File, guide: GuideRect) {
        setReadingStatus(pageId, "Recognising the card for pocket ${position + 1}…")
        viewModelScope.launch {
            val message = runCatching {
                if (cardCount.value == 0) return@runCatching "The card list isn't downloaded yet, so cards can't be recognised. Open search on the shelf and download it first."
                ensurePicturesList(pageId)
                setReadingStatus(pageId, "Recognising the card for pocket ${position + 1}…")
                val scanned = PageReader.read(photo.absolutePath, guide, columns = 1, rows = 1).firstOrNull()
                    ?: return@runCatching "Couldn't open the photo. Try scanning the card again."
                setScanPicture(pageId, position, scanned.picture)
                val found = recognise(scanned.read.copy(position = position), scanned.picture)
                val match = found.card
                when {
                    match != null -> {
                        setSuggestions(pageId, position, emptyList())
                        pageDao.upsertPocket(Pocket(pageId, position, cardId = match.id))
                        recordCard(pageId, match)
                        "Pocket ${position + 1}: ${match.name} (${match.setName} ${match.number})."
                    }
                    found.guesses.isNotEmpty() -> {
                        // Guesses from the expansion they've been scanning go first.
                        val streak = expansionStreak(pageId)?.setId
                        setSuggestions(pageId, position, ExpansionStreak.preferSet(found.guesses, streak) { it.setId })
                        _openPocket.value = pageId to position
                        "Not sure which card is in pocket ${position + 1}. Pick it from the best guesses."
                    }
                    else -> "Couldn't recognise the card for pocket ${position + 1}. Try again closer, with less glare, or choose it from the list."
                }
            }.getOrElse { "Couldn't read the card. Tap the pocket to choose it." }
            File(photo.absolutePath).delete()
            setReadingStatus(pageId, message)
        }
    }

    fun setPocketCard(pageId: String, position: Int, cardId: String) {
        setSuggestions(pageId, position, emptyList())
        viewModelScope.launch {
            pageDao.upsertPocket(Pocket(pageId, position, cardId = cardId))
            cardIndex.get(cardId)?.let { recordCard(pageId, it) }
        }
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

// How many look-alikes the picture model hands to detail matching.
private const val SHORTLIST = 20
