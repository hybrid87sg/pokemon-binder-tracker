package com.hybrid87sg.bindertracker.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Card pictures saved on the phone for good, so binders and Missing cards
 * still show their artwork with no internet.
 *
 * How it works, in plain words:
 * - Each card's small picture is saved as a file in card-art/ inside the
 *   app's own storage (like card-art/sv03.5-025.img). Android never clears
 *   this folder on its own, unlike the normal picture cache.
 * - Cards in your binders are saved as soon as they're added, on any
 *   internet connection (each one is only about 30 KB).
 * - Cards from the expansions your binders collect (the Missing cards
 *   checklist) are saved too, on Wi-Fi only unless you allow mobile data.
 * - Pictures nobody needs any more (a card taken out, an expansion no longer
 *   collected) are deleted, so the folder doesn't keep growing.
 *
 * Card pictures on screen ([com.hybrid87sg.bindertracker.ui.components.CardImage])
 * look in this folder first, then fall back to the internet like before.
 */
object OfflineArt {
    private const val FOLDER = "card-art"
    private val UNSAFE = Regex("[^A-Za-z0-9._-]")

    /** Where a card's saved picture is kept. */
    fun file(context: Context, cardId: String): File =
        File(File(context.filesDir, FOLDER), fileName(cardId))

    /** The card's saved picture, or null if it isn't saved (yet). */
    fun find(context: Context, cardId: String?): File? {
        val id = cardId ?: return null
        return if (fileName(id) in savedNames(context)) file(context, id) else null
    }

    // The names of the saved pictures, read from the folder once and then kept
    // up to date, so scrolling a long list doesn't ask the phone's storage
    // about every card as it comes into view.
    @Volatile private var saved: MutableSet<String>? = null

    private fun savedNames(context: Context): Set<String> = saved ?: synchronized(this) {
        saved ?: java.util.concurrent.ConcurrentHashMap.newKeySet<String>().also { names ->
            folder(context).list()?.let { names.addAll(it) }
            saved = names
        }
    }

    /** A picture was just saved as [name]. */
    internal fun noteSaved(name: String) = synchronized(this) { saved?.add(name); Unit }

    /** Pictures were deleted: read the folder again next time.
     *  Both notes take the same lock as [savedNames], so a change that
     *  happens while the folder is being read isn't lost. */
    internal fun noteChanged() = synchronized(this) { saved = null }

    /**
     * Card ids can hold characters a file name can't, so keep only safe ones.
     * The ".img" ending is on purpose: pictures come as WebP or PNG, and the
     * picture loader works out which by looking inside the file.
     */
    fun fileName(cardId: String): String = cardId.replace(UNSAFE, "_") + ".img"

    /**
     * What to save. Binder cards always; cards from collected expansions that
     * aren't in a binder only when [includeMissing] is on. A card in both
     * counts as a binder card.
     */
    fun plan(binderCards: List<CardArt>, expansionCards: List<CardArt>, includeMissing: Boolean): ArtPlan {
        val binder = binderCards.distinctBy { it.id }
        val inBinder = binder.mapTo(HashSet()) { it.id }
        val missing = expansionCards.filter { it.id !in inBinder }.distinctBy { it.id }
        return ArtPlan(binder, missing, includeMissing)
    }

    internal fun folder(context: Context) = File(context.filesDir, FOLDER)
}

/** The cards [OfflineArt] should keep pictures of. */
data class ArtPlan(val binder: List<CardArt>, val missing: List<CardArt>, val includeMissing: Boolean) {
    /** Every card whose picture should stay on the phone. */
    val keep: Set<String> by lazy {
        val ids = binder.mapTo(HashSet()) { it.id }
        if (includeMissing) missing.mapTo(ids) { it.id }
        ids
    }
}

/** What the Me tab shows under "Offline artwork". */
data class OfflineArtStatus(
    val binderSaved: Int = 0,
    val binderTotal: Int = 0,
    val missingSaved: Int = 0,
    val missingTotal: Int = 0,
    val bytes: Long = 0,
    val saveMissing: Boolean = true,
    val useMobileData: Boolean = false,
    // Why saving is paused, or null if it isn't.
    val waiting: ArtWaiting? = null,
    // True while pictures are being downloaded.
    val busy: Boolean = false,
)

enum class ArtWaiting { NoInternet, Wifi }

/**
 * Does the saving in the background while the app is open. Watches your
 * binders, the expansions they collect, the two switches on the Me tab and
 * the phone's connection, and starts again whenever one of them changes.
 */
class OfflineArtSaver(
    private val context: Context,
    private val dao: ChecklistDao,
    scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("offline_art", Context.MODE_PRIVATE)
    private val saveMissing = MutableStateFlow(prefs.getBoolean(KEY_MISSING, true))
    private val useMobileData = MutableStateFlow(prefs.getBoolean(KEY_DATA, false))
    // Goes up when "Download again" is tapped.
    private val restart = MutableStateFlow(0)

    private val _status = MutableStateFlow(OfflineArtStatus(saveMissing = saveMissing.value, useMobileData = useMobileData.value))
    val status: StateFlow<OfflineArtStatus> = _status.asStateFlow()

    // Cards with no picture on either website. Not tried again until the app restarts.
    private val noPicture = HashSet<String>()

    init {
        scope.launch { run() }
    }

    fun setSaveMissing(on: Boolean) {
        prefs.edit().putBoolean(KEY_MISSING, on).apply()
        saveMissing.value = on
    }

    fun setUseMobileData(on: Boolean) {
        prefs.edit().putBoolean(KEY_DATA, on).apply()
        useMobileData.value = on
    }

    /** Throws away every saved picture and saves them all again (in case some were wrong). */
    fun downloadAgain() {
        synchronized(noPicture) { noPicture.clear() }
        restart.value++
    }

    @OptIn(FlowPreview::class)
    private suspend fun run() {
        // Null until the card list is on the phone (then we can't tell which cards an expansion has).
        val expansionCards: Flow<List<CardArt>?> = combine(dao.observeFollowedSets(), dao.observeCardCount()) { sets, count ->
            if (count == 0) null else sets.flatMap(Binder::splitSetIds).distinct().sorted()
        }.distinctUntilChanged().map { ids ->
            // SQLite takes at most 999 values in one "IN (...)", so ask in chunks.
            ids?.chunked(500)?.flatMap { dao.cardArtInSets(it) }
        }
        val settings = combine(saveMissing, useMobileData, restart) { m, d, r -> Triple(m, d, r) }
        var lastRestart = restart.value
        combine(dao.observeBinderCardArt(), expansionCards, settings, connection()) { binder, sets, s, net ->
            Inputs(OfflineArt.plan(binder, sets.orEmpty(), s.first), s.second, s.third, net, cardListReady = sets != null)
        }
            // Wait for things to settle (e.g. a whole page being filled) before starting.
            .debounce(1_500)
            .collectLatest { inputs ->
                withContext(Dispatchers.IO) {
                    if (inputs.restart != lastRestart) {
                        lastRestart = inputs.restart
                        OfflineArt.folder(context).deleteRecursively()
                        OfflineArt.noteChanged()
                    }
                    savePass(inputs)
                }
            }
    }

    private suspend fun savePass(inputs: Inputs) {
        val plan = inputs.plan
        val folder = OfflineArt.folder(context).apply { mkdirs() }

        // Tidy up pictures nobody needs any more. Not while the card list is
        // missing or being downloaded again, or every Missing card would be thrown away.
        if (inputs.cardListReady) {
            val keepNames = plan.keep.mapTo(HashSet()) { OfflineArt.fileName(it) }
            folder.listFiles()?.forEach { f -> if (f.name !in keepNames) f.delete() }
            OfflineArt.noteChanged()
        }

        fun saved(c: CardArt) = OfflineArt.file(context, c.id).isFile
        val binderSaved = AtomicInteger(plan.binder.count(::saved))
        val missingSaved = AtomicInteger(if (plan.includeMissing) plan.missing.count(::saved) else 0)
        val bytes = AtomicLong(folder.listFiles()?.sumOf { it.length() } ?: 0L)
        fun publish(waiting: ArtWaiting?, busy: Boolean = false) = _status.update {
            OfflineArtStatus(
                binderSaved = binderSaved.get(),
                binderTotal = plan.binder.size,
                missingSaved = missingSaved.get(),
                missingTotal = if (plan.includeMissing) plan.missing.size else 0,
                bytes = bytes.get(),
                saveMissing = plan.includeMissing,
                useMobileData = inputs.useMobileData,
                waiting = waiting,
                busy = busy,
            )
        }

        val net = inputs.connection
        val binderTodo = plan.binder.filterNot(::saved)
        val missingTodo = if (plan.includeMissing) plan.missing.filterNot(::saved) else emptyList()
        if (!net.online) {
            publish(if (binderTodo.isEmpty() && missingTodo.isEmpty()) null else ArtWaiting.NoInternet)
            return
        }
        publish(null, busy = binderTodo.isNotEmpty() || missingTodo.isNotEmpty())

        // Binder cards first, on any connection.
        if (!saveAll(binderTodo, binderSaved, bytes) { publish(null, busy = true) }) {
            publish(ArtWaiting.NoInternet)
            return
        }
        if (missingTodo.isEmpty()) return publish(null)
        if (net.metered && !inputs.useMobileData) return publish(ArtWaiting.Wifi)
        publish(null, busy = true)
        if (!saveAll(missingTodo, missingSaved, bytes) { publish(null, busy = true) }) {
            publish(ArtWaiting.NoInternet)
            return
        }
        publish(null)
    }

    /** Saves each card's picture, 4 at a time. False if the internet dropped out part way. */
    private suspend fun saveAll(cards: List<CardArt>, counter: AtomicInteger, bytes: AtomicLong, onSaved: () -> Unit): Boolean {
        if (cards.isEmpty()) return true
        val gate = Semaphore(4)
        val lostInternet = AtomicBoolean(false)
        coroutineScope {
            for (card in cards) {
                if (lostInternet.get()) break
                if (synchronized(noPicture) { card.id in noPicture }) continue
                gate.acquire()
                launch {
                    try {
                        if (lostInternet.get()) return@launch
                        when (val size = saveOne(card)) {
                            null -> synchronized(noPicture) { noPicture += card.id }
                            else -> {
                                counter.incrementAndGet()
                                bytes.addAndGet(size)
                                onSaved()
                            }
                        }
                    } catch (_: IOException) {
                        lostInternet.set(true)
                    } finally {
                        gate.release()
                    }
                }
            }
        }
        return !lostInternet.get()
    }

    /**
     * Downloads one card's picture: TCGdex first, then the pokemontcg.io
     * backup. Returns the file size, or null if neither site has a picture.
     * Throws IOException when the internet itself isn't working.
     */
    private fun saveOne(card: CardArt): Long? {
        val sources = listOfNotNull(card.imageUrl?.let { "$it/low.webp" }, CardPictures.backupUrl(card.id, large = false))
        for (url in sources) {
            val data = fetch(url) ?: continue
            val target = OfflineArt.file(context, card.id)
            val temp = File(target.parentFile, target.name + ".tmp")
            temp.writeBytes(data)
            if (!temp.renameTo(target)) {
                temp.delete()
                continue
            }
            OfflineArt.noteSaved(target.name)
            return data.size.toLong()
        }
        return null
    }

    /** The picture's bytes, or null if the site says it has none. */
    private fun fetch(url: String): ByteArray? {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                // 404 and friends: no picture there. 5xx: the site is having trouble, try the backup.
                return null
            }
            val type = conn.contentType.orEmpty()
            val data = conn.inputStream.use { it.readBytes() }
            // A tiny or non-picture answer isn't a card picture.
            if (data.size < 512 || (type.isNotEmpty() && !type.startsWith("image/") && !type.startsWith("application/octet-stream"))) return null
            return data
        } catch (_: FileNotFoundException) {
            return null
        } finally {
            conn.disconnect()
        }
    }

    /** Whether the phone is online, and whether the connection costs data (mobile data). */
    private fun connection(): Flow<Connection> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        fun current(): Connection {
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
                ?: return Connection(online = false, metered = true)
            val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return Connection(online, metered)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(current()) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(current()) }
        }
        trySend(current())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private data class Connection(val online: Boolean, val metered: Boolean)
    private data class Inputs(
        val plan: ArtPlan,
        val useMobileData: Boolean,
        val restart: Int,
        val connection: Connection,
        val cardListReady: Boolean,
    )

    private companion object {
        const val KEY_MISSING = "save_missing"
        const val KEY_DATA = "use_mobile_data"
    }
}
