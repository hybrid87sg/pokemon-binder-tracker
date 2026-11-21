package com.hybrid87sg.bindertracker

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hybrid87sg.bindertracker.auth.AuthManager
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderSize
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.data.CardIndex
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.data.PageSummary
import com.hybrid87sg.bindertracker.data.Pocket
import com.hybrid87sg.bindertracker.data.SeriesOption
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.scan.CardRecognizer
import com.hybrid87sg.bindertracker.scan.DetailMatcher
import com.hybrid87sg.bindertracker.scan.GuessPicker
import com.hybrid87sg.bindertracker.scan.PageReader
import com.hybrid87sg.bindertracker.scan.PocketMatcher
import com.hybrid87sg.bindertracker.scan.ReadCard
import com.hybrid87sg.bindertracker.scan.ReadingSummary
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

    suspend fun searchCards(query: String, seriesId: String = ""): List<Card> = cardIndex.search(query, seriesId)

    suspend fun seriesList(): List<SeriesOption> = cardIndex.series()

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
     * Reads the cards in a page photo and fills in the pockets it's sure
     * about. Runs in the background, so the page opens straight away and
     * pockets fill in a moment later.
     *
     * [rescan] = the person took a new photo of a page that already exists.
     * Then a pocket also changes when the camera clearly sees a different
     * card there (the card was swapped). Pockets it can't read stay as they were.
     */
    private fun readCards(pageId: String, binder: Binder, photo: File, guide: GuideRect, rescan: Boolean = false) {
        setReadingStatus(pageId, "Reading the cards…")
        viewModelScope.launch {
            val result = runCatching {
                if (cardCount.value == 0) return@runCatching "The card list isn't downloaded yet, so cards can't be recognised. Open search on the shelf and download it, then rescan this page."
                val pictures = ensurePicturesList(pageId)
                setReadingStatus(pageId, "Reading the cards…")
                val read = PageReader.read(photo.absolutePath, guide, binder.columns, binder.rows)
                val current = pageDao.pocketsOf(pageId).associateBy { it.position }
                var recognised = 0
                var changed = 0
                var unmatched = 0
                var byPicture = 0
                for ((card, picture) in read) {
                    if (card.looksEmpty && picture == null) continue
                    setScanPicture(pageId, card.position, picture)
                    val found = recognise(card, picture)
                    // No text at all usually means an empty pocket, so a picture
                    // match there is only offered as a guess, never filled in.
                    val match = if (card.looksEmpty) null else found.card
                    setSuggestions(pageId, card.position, if (match == null) found.guesses.takeUnless { card.looksEmpty && current[card.position]?.isEmpty == true }.orEmpty() else emptyList())
                    if (match == null) { if (!card.looksEmpty) unmatched++; continue }
                    recognised++
                    if (found.fromPicture) byPicture++
                    val before = current[card.position]
                    if (before?.cardId == match.id) continue
                    if (rescan) {
                        pageDao.upsertPocket(Pocket(pageId, card.position, cardId = match.id))
                        changed++
                    } else {
                        changed += pageDao.fillPocketIfUnset(pageId, card.position, match.id)
                    }
                }
                val summary = ReadingSummary(binder.pocketsPerPage, recognised, changed, unmatched, rescan, byPicture).message()
                if (pictures) summary else "$summary (Couldn't download the card pictures list, so only text was used.)"
            }
            setReadingStatus(
                pageId,
                result.getOrElse { "Couldn't read the cards automatically. Tap a pocket to choose its card." },
            )
        }
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
                        "Pocket ${position + 1}: ${match.name} (${match.setName} ${match.number})."
                    }
                    found.guesses.isNotEmpty() -> {
                        setSuggestions(pageId, position, found.guesses)
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

    /**
     * Takes a new photo for an existing page: swaps the photo, then reads it
     * again and updates any pockets whose card changed.
     */
    fun rescanPage(page: Page, binder: Binder, photo: File, guide: GuideRect) {
        viewModelScope.launch {
            page.photoPath?.let { old -> if (old != photo.absolutePath) File(old).delete() }
            pageDao.updatePhoto(page.id, photo.absolutePath, guide.left, guide.top, guide.right, guide.bottom)
            readCards(page.id, binder, photo, guide, rescan = true)
        }
    }

    fun setPocketCard(pageId: String, position: Int, cardId: String) {
        setSuggestions(pageId, position, emptyList())
        viewModelScope.launch { pageDao.upsertPocket(Pocket(pageId, position, cardId = cardId)) }
    }

    fun setPocketEmpty(pageId: String, position: Int) {
        viewModelScope.launch { pageDao.upsertPocket(Pocket(pageId, position, isEmpty = true)) }
    }

    fun clearPocket(pageId: String, position: Int) {
        viewModelScope.launch { pageDao.upsertPocket(Pocket(pageId, position)) }
    }

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
