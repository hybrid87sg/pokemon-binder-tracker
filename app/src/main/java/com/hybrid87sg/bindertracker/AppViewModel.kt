package com.hybrid87sg.bindertracker

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hybrid87sg.bindertracker.auth.AuthManager
import com.hybrid87sg.bindertracker.data.BinderMissing
import com.hybrid87sg.bindertracker.backup.BackupFile
import com.hybrid87sg.bindertracker.backup.BinderBackup
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderResize
import com.hybrid87sg.bindertracker.data.BinderSize
import com.hybrid87sg.bindertracker.data.Card
import com.hybrid87sg.bindertracker.prices.ExchangeRates
import com.hybrid87sg.bindertracker.prices.SgdRates
import com.hybrid87sg.bindertracker.data.CardSearch
import com.hybrid87sg.bindertracker.data.OwnedFirst
import com.hybrid87sg.bindertracker.data.Checklist
import com.hybrid87sg.bindertracker.data.ChecklistData
import com.hybrid87sg.bindertracker.data.ChecklistProgress
import com.hybrid87sg.bindertracker.data.ExpansionCount
import com.hybrid87sg.bindertracker.data.CardIndex
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.data.PageSummary
import com.hybrid87sg.bindertracker.data.BinderFill
import com.hybrid87sg.bindertracker.data.Pocket
import com.hybrid87sg.bindertracker.data.ViewPrefs
import com.hybrid87sg.bindertracker.data.ExpansionOption
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.data.OwnPhotos
import com.hybrid87sg.bindertracker.data.OfflineArtSaver
import com.hybrid87sg.bindertracker.data.OfflineArtStatus
import com.hybrid87sg.bindertracker.scan.CardMoves
import com.hybrid87sg.bindertracker.scan.CardRecognizer
import com.hybrid87sg.bindertracker.scan.LiveCheck
import com.hybrid87sg.bindertracker.scan.LiveStep
import com.hybrid87sg.bindertracker.scan.MoveMatcher
import com.hybrid87sg.bindertracker.scan.DetailMatcher
import com.hybrid87sg.bindertracker.scan.ExpansionStreak
import com.hybrid87sg.bindertracker.scan.GuessPicker
import com.hybrid87sg.bindertracker.scan.PageReader
import com.hybrid87sg.bindertracker.scan.PocketMatcher
import com.hybrid87sg.bindertracker.scan.ReadCard
import com.hybrid87sg.bindertracker.sync.CloudSync
import com.hybrid87sg.bindertracker.sync.InviteCodes
import com.hybrid87sg.bindertracker.sync.InvitePreview
import com.hybrid87sg.bindertracker.sync.SharingInfo
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

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

    /** View choices kept on this phone only, like Main set / Master set (see ViewPrefs). */
    private val viewPrefs = ViewPrefs(app)

    /** The signed-in person's binders. Switches automatically when the user changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val binders: StateFlow<List<Binder>> = user
        .flatMapLatest { u -> if (u == null) flowOf(emptyList()) else binderDao.observeBinders(u.uid) }
        // Swap in this phone's own Main/Master choice for each binder.
        .combine(viewPrefs.masterSets) { list, chosen -> list.map { viewPrefs.personal(it, chosen) } }
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

    /** Keeps binders in the Google account and handles sharing (see CloudSync). */
    private val cloud = CloudSync(app, db, viewModelScope)

    /** Who each binder is shared with, by binder ID. */
    val sharing: StateFlow<Map<String, SharingInfo>> = cloud.sharing

    /** Why online saving isn't working right now, or null. */
    val syncProblem: StateFlow<String?> = cloud.problem

    /** True while binders are kept online (signed in with Google and Firebase set up). */
    val syncActive: StateFlow<Boolean> = cloud.active

    init {
        // Whenever someone is signed in with Google, bring over any binders
        // made before signing in ("phone only" mode), so that work is never
        // hidden. This also rescues binders from before this update, for
        // people who already signed in and lost sight of them.
        // Then start keeping their binders online.
        viewModelScope.launch {
            user.collect { u ->
                if (u != null && !u.isLocalOnly) {
                    val moved = binderDao.moveBinders(fromOwner = AuthManager.LOCAL_USER.uid, toOwner = u.uid)
                    if (moved > 0) {
                        _movedBindersNotice.value =
                            if (moved == 1) "1 binder added to your account." else "$moved binders added to your account."
                    }
                    cloud.start(u)
                    if (moved > 0) cloud.uploadMissing()
                } else {
                    cloud.stop()
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

    fun binder(id: String): Flow<Binder?> =
        binderDao.observeBinder(id).combine(viewPrefs.masterSets) { b, chosen -> b?.let { viewPrefs.personal(it, chosen) } }

    /**
     * Main set / Master set for one binder, on this phone only. Anyone can
     * pick it (owner, Editor or Viewer); it isn't sent online, so friends
     * sharing the binder keep their own choice.
     */
    fun setMasterSet(binderId: String, master: Boolean) = viewPrefs.setMasterSet(binderId, master)

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
        val binder = Binder(
            ownerId = owner.uid,
            name = name.trim(),
            colorHex = colorHex,
            columns = size.columns,
            rows = size.rows,
        )
        viewModelScope.launch {
            binderDao.insert(binder)
            cloud.binderCreated(binder)
        }
    }

    /** Repaints a binder's spine. [colorHex] is text like "#F2C230". */
    fun setBinderColor(binder: Binder, colorHex: String) {
        if (!binder.canEdit) return
        viewModelScope.launch {
            binderDao.updateColor(binder.id, colorHex)
            cloud.binderSettingsChanged(binder.copy(colorHex = colorHex))
        }
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
    fun updateBinder(
        binder: Binder,
        name: String,
        colorHex: String,
        size: BinderSize,
        // What it collects (Missing cards). Null = leave as it is.
        collecting: List<String>? = null,
        countSecretRares: Boolean? = null,
    ) {
        if (!binder.canEdit) return
        // Main/Master is this phone's own choice, so it's saved here, not on the shared binder.
        countSecretRares?.let { viewPrefs.setMasterSet(binder.id, it) }
        viewModelScope.launch {
            val stored = binderDao.get(binder.id) ?: binder
            val updated = stored.copy(
                name = name.trim(),
                colorHex = colorHex,
                columns = size.columns,
                rows = size.rows,
                followedSets = collecting?.let(Binder::joinSetIds) ?: stored.followedSets,
            )
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
                binderDao.update(updated)
            }
            // Send the new settings; a new size changes pockets on every page, so send those too.
            if (size.pockets != binder.pocketsPerPage) cloud.binderResized(binder.id) else cloud.binderSettingsChanged(updated)
        }
    }

    /**
     * Your own binder: deletes it (for everyone it was shared with too).
     * A binder shared with you: takes it off your shelf, and you leave it.
     */
    fun deleteBinder(binder: Binder) {
        viewModelScope.launch {
            val pages = pageDao.pagesOf(binder.id)
            cloud.binderRemoved(binder, pages.map { it.id })
            // Remove the binder's pages and their photos too.
            pages.forEach { page ->
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

    /** Card search, with the cards already in your binders listed first. */
    suspend fun searchCards(query: String, seriesId: String = "", setId: String = ""): CardSearch {
        // Look further than the 100 we show, so your own cards are found even
        // when lots of other cards match too.
        val found = cardIndex.search(query, seriesId, setId, limit = 2000)
        val places = runCatching { checklistDao.cardPlaces() }.getOrDefault(emptyList())
        return OwnedFirst.arrange(found, places)
    }

    suspend fun expansionList(): List<ExpansionOption> = cardIndex.expansions()

    private val exchangeRates = ExchangeRates(app)

    /** Today's rates for showing card prices in Singapore dollars, or null if we have none. */
    suspend fun sgdRates(): SgdRates? = exchangeRates.get()

    suspend fun card(id: String): Card? = cardIndex.get(id)

    /** Card details and market prices, fetched live from TCGdex. */
    suspend fun cardDetails(id: String): Result<CardDetails> = runCatching { Tcgdex.cardDetails(id) }

    // ---- Missing cards (the checklist for binders that collect an expansion) ----

    private val checklistDao = db.checklistDao()

    // ---- Offline artwork (card pictures saved on the phone, see OfflineArt) ----

    private val offlineArt = OfflineArtSaver(app, checklistDao, viewModelScope)

    /** How many card pictures are saved for offline use, for the Me tab. */
    val offlineArtStatus: StateFlow<OfflineArtStatus> = offlineArt.status

    fun setSaveMissingArt(on: Boolean) = offlineArt.setSaveMissing(on)
    fun setArtOnMobileData(on: Boolean) = offlineArt.setUseMobileData(on)
    fun downloadArtAgain() = offlineArt.downloadAgain()

    /**
     * How many cards each collecting binder is still missing, by binder ID,
     * for the badges on the shelf. Binders that collect nothing aren't listed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val missingCounts: StateFlow<Map<String, ChecklistProgress>> =
        combine(binders, checklistDao.observeOwnedCards(), cardCount) { list, owned, _ -> list to owned }
            .mapLatest { (list, owned) ->
                val collecting = list.filter { it.followedSetIds.isNotEmpty() }
                if (collecting.isEmpty()) return@mapLatest emptyMap<String, ChecklistProgress>()
                val cards = checklistDao.cardsInSets(collecting.flatMap { it.followedSetIds }.distinct())
                if (cards.isEmpty()) return@mapLatest emptyMap<String, ChecklistProgress>()
                val ownedBy = owned.groupBy({ it.binderId }, { it.cardId }).mapValues { it.value.toSet() }
                collecting.associate { b ->
                    val entries = Checklist.entries(cards, b.followedSetIds, ownedBy[b.id].orEmpty(), b.countSecretRares)
                    b.id to ChecklistProgress(missing = entries.count { !it.owned }, total = entries.size)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The Missing tab: every collecting binder with the cards it still
     * needs, in the binder's own order. Kept up to date like the badges.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allMissing: StateFlow<List<BinderMissing>> =
        combine(binders, checklistDao.observeOwnedCards(), cardCount) { list, owned, _ -> list to owned }
            .mapLatest { (list, owned) ->
                val collecting = list.filter { it.followedSetIds.isNotEmpty() }
                if (collecting.isEmpty()) return@mapLatest emptyList<BinderMissing>()
                val cards = checklistDao.cardsInSets(collecting.flatMap { it.followedSetIds }.distinct())
                val ownedBy = owned.groupBy({ it.binderId }, { it.cardId }).mapValues { it.value.toSet() }
                collecting.map { b ->
                    val entries = Checklist.entries(cards, b.followedSetIds, ownedBy[b.id].orEmpty(), b.countSecretRares)
                    BinderMissing(b, entries.filter { !it.owned }, ChecklistProgress(entries.count { !it.owned }, entries.size))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything the Missing cards page needs for one binder, kept up to date. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun checklist(binderId: String): Flow<ChecklistData?> =
        combine(binder(binderId), checklistDao.observeBinderPockets(binderId), cardCount) { b, pockets, _ -> b to pockets }
            .mapLatest { (b, pockets) ->
                if (b == null) return@mapLatest null
                val cards = if (b.followedSetIds.isEmpty()) emptyList() else checklistDao.cardsInSets(b.followedSetIds)
                ChecklistData(b, cards, pockets, pockets.mapNotNull { it.cardId }.toSet())
            }

    /** The expansion most of a binder's cards come from, to suggest collecting it. */
    suspend fun topExpansion(binderId: String): ExpansionCount? = checklistDao.topExpansion(binderId)

    /**
     * Saves which expansions a binder collects (shared; owner or Editors).
     * Main/Master set is this phone's own choice, see [setMasterSet].
     */
    fun setCollecting(binder: Binder, setIds: List<String>) {
        if (!binder.canEdit) return
        viewModelScope.launch {
            val stored = binderDao.get(binder.id) ?: return@launch
            val updated = stored.copy(followedSets = Binder.joinSetIds(setIds))
            if (updated == stored) return@launch
            binderDao.update(updated)
            cloud.binderSettingsChanged(updated)
        }
    }

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
            val pages = List(count) { i -> Page(binderId = binder.id, photoPath = null, createdAt = now + i) }
            pages.forEach { pageDao.createPage(it, binder.pocketsPerPage) }
            cloud.pagesAdded(binder.id, pages)
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

    /**
     * Keeps the scan of this pocket (if it was just scanned) as the card's own
     * photo, straightened. It only shows when no website has a picture of the
     * card, e.g. brand-new sets (see OwnPhotos).
     */
    private suspend fun keepOwnPhoto(pageId: String, position: Int, cardId: String) {
        val picture = _scanPictures.value[pageId]?.get(position) ?: return
        withContext(Dispatchers.Default) {
            runCatching {
                val flat = DetailMatcher.straighten(picture) ?: picture
                OwnPhotos.save(getApplication(), cardId, flat)
            }
        }
    }

    /** A pocket whose menu should open by itself (after scanning one card it wasn't sure about). */
    private val _openPocket = MutableStateFlow<Pair<String, Int>?>(null)
    val openPocket: StateFlow<Pair<String, Int>?> = _openPocket.asStateFlow()

    fun pocketOpened() { _openPocket.value = null }

    /**
     * The cards the card screen can swipe through, set just before opening it
     * (a binder's cards, the Missing cards list, or search results).
     */
    var browse: com.hybrid87sg.bindertracker.ui.screens.CardBrowse? = null

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
                        savePocket(Pocket(pageId, position, cardId = match.id))
                        keepOwnPhoto(pageId, position, match.id)
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

    // ---- Live scanning (the camera reads the card a few times a second) ----

    private val cardMoves = CardMoves.get(app)
    private val scanPrefs = app.getSharedPreferences("scan", Context.MODE_PRIVATE)

    /** After a card is added, go straight on to the next empty pocket. */
    private val _autoNextPocket = MutableStateFlow(scanPrefs.getBoolean("auto_next", true))
    val autoNextPocket: StateFlow<Boolean> = _autoNextPocket.asStateFlow()

    fun setAutoNextPocket(on: Boolean) {
        _autoNextPocket.value = on
        scanPrefs.edit().putBoolean("auto_next", on).apply()
    }

    /** True once the card pictures list (for the artwork step) is on the phone. */
    val picturesListReady: Boolean get() = recognizer.isDownloaded

    /**
     * The quick checks, run on every look: card number and set, then name,
     * then moves. Only reads the phone's card list (and TCGdex for moves the
     * first time a name is seen), so it's fast enough to run live.
     */
    suspend fun liveCheck(read: ReadCard): LiveCheck {
        if (read.name == null && read.number == null) return LiveCheck.NoCard
        val dao = db.cardDao()
        // 1. Card number, with the set size after the slash ("025/198") to pick the set.
        var numberFound = false
        read.number?.let { number ->
            val withNumber = dao.allWithNumber(number)
            numberFound = withNumber.isNotEmpty()
            val inSet = read.setTotal?.let { total -> withNumber.filter { it.setOfficial == total } }.orEmpty()
            PocketMatcher.pick(read, inSet.ifEmpty { withNumber })?.let { return LiveCheck.Sure(it, LiveStep.Number) }
            // The name wasn't readable but only one set has this number and size.
            if (read.name == null && inSet.size == 1) return LiveCheck.Sure(inSet.first(), LiveStep.Number)
        }
        val name = read.name ?: return LiveCheck.Unsure(null, emptyList(), 0, false, numberFound)
        // 2. The name: which cards are called this.
        val named = PocketMatcher.sameName(name, dao.searchByName(name, limit = 400))
        if (named.isEmpty()) return LiveCheck.Unsure(name, emptyList(), 0, false, numberFound)
        if (named.size == 1) return LiveCheck.Sure(named.first(), LiveStep.Name, 1)
        // If the set size was read, cards from sets of that size come first.
        val sameName = read.setTotal?.let { total -> named.filter { it.setOfficial == total } }?.ifEmpty { null } ?: named
        if (sameName.size == 1) return LiveCheck.Sure(sameName.first(), LiveStep.Name, named.size)
        // 3. The moves: tell apart cards with the same name.
        if (sameName.size <= MAX_MOVE_LOOKUPS) {
            val moves = runCatching { cardMoves.movesOf(sameName) }.getOrDefault(emptyMap())
            val ids = MoveMatcher.narrow(read.text, moves)
            val narrowed = sameName.filter { it.id in ids }
            if (narrowed.size == 1) return LiveCheck.Sure(narrowed.first(), LiveStep.Moves, named.size)
            if (narrowed.isNotEmpty()) return LiveCheck.Unsure(name, narrowed, named.size, narrowed.size < sameName.size, numberFound)
        }
        return LiveCheck.Unsure(name, sameName, named.size, false, numberFound)
    }

    /**
     * 4. The artwork: compares the picture with the cards still in the
     * running (all cards if [shortlist] is empty). Slower, so it runs once
     * per card, not on every look. Null when it isn't sure.
     */
    suspend fun liveArtwork(read: ReadCard, picture: Bitmap, shortlist: List<Card>): Card? = runCatching {
        if (!recognizer.isDownloaded) recognizer.download()
        if (shortlist.isEmpty()) {
            val found = recognise(read, picture)
            return@runCatching found.card
        }
        val flat = withContext(Dispatchers.Default) { DetailMatcher.straighten(picture) } ?: picture
        val candidates = if (shortlist.size > SHORTLIST) {
            val described = recognizer.describe(flat)
            if (described == null) shortlist.take(SHORTLIST) else {
                val (index, numbers) = described
                index.rank(numbers, shortlist.map { it.id }).take(SHORTLIST).mapNotNull { g -> shortlist.firstOrNull { it.id == g.cardId } }
            }
        } else shortlist
        val byDetails = DetailMatcher.pickByDetails(flat, candidates.mapNotNull { c -> c.imageUrl?.let { c.id to it } })
        DetailMatcher.confident(byDetails)?.let { id -> candidates.firstOrNull { it.id == id } }
    }.getOrNull()

    /** Live scanning found the card: put it in the pocket. */
    fun livePlace(pageId: String, position: Int, card: Card, picture: Bitmap?) {
        setSuggestions(pageId, position, emptyList())
        setScanPicture(pageId, position, picture)
        viewModelScope.launch {
            savePocket(Pocket(pageId, position, cardId = card.id))
            keepOwnPhoto(pageId, position, card.id)
            recordCard(pageId, card)
            setReadingStatus(pageId, "Pocket ${position + 1}: ${card.name} (${card.setName} ${card.number}).")
        }
    }

    /**
     * Live scanning wasn't sure: keep the picture and best guesses for the
     * pocket, so the card picked in search keeps this scan as its picture
     * if no website has one (see [setPocketCard]).
     */
    fun liveUnsure(pageId: String, position: Int, picture: Bitmap?, guesses: List<Card>) {
        setScanPicture(pageId, position, picture)
        setSuggestions(pageId, position, guesses.take(5))
    }

    fun setPocketCard(pageId: String, position: Int, cardId: String) {
        // Guesses waiting here mean the pocket was just scanned and the app
        // wasn't sure, so the scan shows this card. Otherwise any scan kept
        // for this pocket may be of a different card.
        val justScanned = _suggestions.value[pageId]?.get(position).orEmpty().isNotEmpty()
        setSuggestions(pageId, position, emptyList())
        viewModelScope.launch {
            savePocket(Pocket(pageId, position, cardId = cardId))
            if (justScanned) keepOwnPhoto(pageId, position, cardId)
            cardIndex.get(cardId)?.let { recordCard(pageId, it) }
        }
    }

    fun setPocketEmpty(pageId: String, position: Int) {
        viewModelScope.launch { savePocket(Pocket(pageId, position, isEmpty = true)) }
    }

    fun clearPocket(pageId: String, position: Int) {
        viewModelScope.launch { savePocket(Pocket(pageId, position)) }
    }

    /** Every pocket of a binder, grouped by page (pages in order). */
    fun binderPockets(binderId: String): Flow<Map<String, List<PocketWithCard>>> =
        pageDao.observeBinderPockets(binderId).map { list ->
            list.groupBy { it.pageId }.mapValues { (_, pockets) -> pockets.map { it.withoutPage() } }
        }

    /**
     * Swaps what two pockets hold: dragging a card onto another pocket, or
     * "Move to another page". Doing it again puts things back (that's Undo).
     * The best guesses and camera pictures of each pocket move with it.
     */
    fun swapPockets(pageA: String, positionA: Int, pageB: String, positionB: Int) {
        swapExtras(pageA, positionA, pageB, positionB)
        viewModelScope.launch { swapAndSync(pageA, positionA, pageB, positionB) }
    }

    /**
     * Adds a new empty page at the end of the binder and moves the card in
     * [position] of [pageId] into its first pocket. [onCreated] gets the new page's ID.
     */
    fun moveToNewPage(binder: Binder, pageId: String, position: Int, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val last = pageDao.pagesOf(binder.id).maxOfOrNull { it.createdAt } ?: 0L
            val page = Page(binderId = binder.id, photoPath = null, createdAt = maxOf(System.currentTimeMillis(), last + 1))
            pageDao.createPage(page, binder.pocketsPerPage)
            cloud.pagesAdded(binder.id, listOf(page))
            swapExtras(pageId, position, page.id, 0)
            swapAndSync(pageId, position, page.id, 0)
            onCreated(page.id)
        }
    }

    private suspend fun swapAndSync(pageA: String, positionA: Int, pageB: String, positionB: Int) {
        val changed = pageDao.swapPockets(pageA, positionA, pageB, positionB)
        changed.forEach { pocket -> pageDao.getPage(pocket.pageId)?.let { cloud.pocketChanged(it.binderId, pocket) } }
    }

    private fun swapExtras(pageA: String, positionA: Int, pageB: String, positionB: Int) {
        _suggestions.value = swapEntries(_suggestions.value, pageA, positionA, pageB, positionB)
        _scanPictures.value = swapEntries(_scanPictures.value, pageA, positionA, pageB, positionB)
    }

    private fun <T> swapEntries(all: Map<String, Map<Int, T>>, pageA: String, positionA: Int, pageB: String, positionB: Int): Map<String, Map<Int, T>> {
        val a = all[pageA]?.get(positionA)
        val b = all[pageB]?.get(positionB)
        var result = all
        fun put(page: String, position: Int, value: T?) {
            val m = result[page].orEmpty()
            result = result + (page to if (value == null) m - position else m + (position to value))
        }
        put(pageA, positionA, b)
        put(pageB, positionB, a)
        return result
    }

    /** Saves one pocket on the phone, and online so everyone sharing the binder sees it. */
    private suspend fun savePocket(pocket: Pocket) {
        pageDao.upsertPocket(pocket)
        pageDao.getPage(pocket.pageId)?.let { cloud.pocketChanged(it.binderId, pocket) }
    }

    // ---- Sharing ----

    /** Makes a share code for a binder you own; [role] is editor or viewer. */
    fun createInvite(binderId: String, role: String) {
        viewModelScope.launch { cloud.createInvite(binderId, role) }
    }

    fun setMemberRole(binderId: String, uid: String, role: String) = cloud.setRole(binderId, uid, role)

    fun removeMember(binderId: String, uid: String) = cloud.removeMember(binderId, uid)

    fun stopSharing(binderId: String) = cloud.stopSharing(binderId)

    /** The Join screen: looking up a code, then joining. */
    sealed interface JoinState {
        data object Idle : JoinState
        data object Busy : JoinState
        data class Found(val invite: InvitePreview) : JoinState
        data class Failed(val message: String) : JoinState
        data class Joined(val binderName: String) : JoinState
    }

    private val _join = MutableStateFlow<JoinState>(JoinState.Idle)
    val joinState: StateFlow<JoinState> = _join.asStateFlow()

    fun resetJoin() {
        _join.value = JoinState.Idle
    }

    /** Finds which binder a scanned or typed code belongs to. */
    fun lookUpInvite(text: String) {
        val code = InviteCodes.parse(text)
        if (code == null) {
            _join.value = JoinState.Failed("That isn't a binder code. Codes have 8 letters and numbers, like 7Q4K-M2PX.")
            return
        }
        if (!cloud.active.value) {
            _join.value = JoinState.Failed("Sign in with Google to join shared binders.")
            return
        }
        _join.value = JoinState.Busy
        viewModelScope.launch {
            _join.value = runCatching { cloud.lookupInvite(code) }.fold(
                onSuccess = { invite ->
                    when {
                        invite == null -> JoinState.Failed("No binder uses that code. Check it, or ask for a new one.")
                        invite.expired -> JoinState.Failed("That code has run out. Ask your friend for a new one.")
                        binderDao.get(invite.binderId) != null -> JoinState.Failed("“${invite.binderName}” is already on your shelf.")
                        else -> JoinState.Found(invite)
                    }
                },
                onFailure = { JoinState.Failed("Couldn't look up the code. Check your internet connection and try again.") },
            )
        }
    }

    fun joinBinder(invite: InvitePreview) {
        _join.value = JoinState.Busy
        viewModelScope.launch {
            _join.value = runCatching { cloud.join(invite) }.fold(
                onSuccess = { JoinState.Joined(invite.binderName) },
                onFailure = { JoinState.Failed("Couldn't join the binder. Check your internet connection, or ask for a new code.") },
            )
        }
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
                cloud.uploadMissing()
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
            cloud.pageDeleted(page.binderId, page.id)
        }
    }
}

/** Where the page sat in the photo, as fractions (0.0–1.0) of its width and height. */
data class GuideRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

// How many look-alikes the picture model hands to detail matching.
private const val SHORTLIST = 20

// Live scanning looks up moves only when this few cards share the name read.
private const val MAX_MOVE_LOOKUPS = 40
