package com.hybrid87sg.bindertracker.sync

import android.content.Context
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.hybrid87sg.bindertracker.auth.AppUser
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.CoverPictures
import com.hybrid87sg.bindertracker.data.CoverPlacement
import com.google.firebase.firestore.Blob
import com.hybrid87sg.bindertracker.data.Page
import com.hybrid87sg.bindertracker.data.Pocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Date

/** One person who uses a shared binder. */
data class Member(val uid: String, val name: String, val role: String)

/** A share code that is waiting to be used. */
data class InviteCode(val code: String, val role: String, val expiresAt: Long) {
    // Treated as used up a minute early, so it never runs out while a friend is typing it.
    val isValid: Boolean get() = expiresAt > System.currentTimeMillis() + 60_000
}

/** Who a binder is shared with, and its current share codes (one per role). */
data class SharingInfo(val ownerId: String, val members: List<Member>, val invites: Map<String, InviteCode>)

/** What a share code leads to, shown to the friend before they join. */
data class InvitePreview(
    val code: String,
    val binderId: String,
    val binderName: String,
    val ownerName: String,
    val role: String,
    val columns: Int,
    val rows: Int,
    val expired: Boolean,
)

/**
 * Keeps binders in the signed-in person's Google account (Cloud Firestore,
 * an online database from Firebase), and lets them share binders.
 *
 * How it works:
 * - The phone's own database (Room) is still what every screen reads, so
 *   the app is just as fast and works offline.
 * - Every change you make is also sent to Firestore ("write-through").
 *   Without internet, Firestore keeps the change and sends it later.
 * - Firestore tells us whenever a binder you can see changes online (a
 *   change from your other phone, or from a friend), and we copy that
 *   change into the phone's database. That is how a friend's new card
 *   shows up on your screen.
 *
 * What is stored online:
 *   binders/{binderId}            name, colour, size, owner, who can use it
 *   binders/{binderId}/pages/{id} which card is in each pocket
 *   binders/{binderId}/cover/picture  the owner's cover picture (a small
 *                                 JPEG) and where it sits; only the owner
 *                                 can change it
 *   invites/{code}                share codes, which binder and role they give
 * Page photos stay on the phone that took them (storing pictures online
 * needs Firebase's paid plan). Card pictures come from TCGdex, so everyone
 * sees those.
 */
class CloudSync(
    context: Context,
    db: AppDatabase,
    private val scope: CoroutineScope,
    // Cover pictures on this phone, kept in step with the owner's online cover.
    private val covers: CoverPictures? = null,
) {
    /** False when the app was built without Firebase (no google-services.json). */
    val isAvailable: Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val store: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val binderDao = db.binderDao()
    private val pageDao = db.pageDao()
    private val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)

    // Online changes are copied into the phone one at a time, in order.
    private val lock = Mutex()

    private var user: AppUser? = null
    private var bindersListener: ListenerRegistration? = null
    private val pageListeners = mutableMapOf<String, ListenerRegistration>()
    private val coverListeners = mutableMapOf<String, ListenerRegistration>()

    // "binderId:version" covers already sent this run, so a refused upload
    // (for example, rules not published yet) isn't retried over and over.
    private val coversSent = mutableSetOf<String>()

    private val _sharing = MutableStateFlow<Map<String, SharingInfo>>(emptyMap())

    /** Who each binder is shared with, by binder ID. */
    val sharing: StateFlow<Map<String, SharingInfo>> = _sharing.asStateFlow()

    private val _problem = MutableStateFlow<String?>(null)

    /** A short message when online saving isn't working, or null when all is well. */
    val problem: StateFlow<String?> = _problem.asStateFlow()

    private val _active = MutableStateFlow(false)

    /** True while binders are being kept online (signed in with Google). */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Starts keeping [u]'s binders online. "Phone only" mode never syncs. */
    fun start(u: AppUser) {
        if (!isAvailable || u.isLocalOnly) {
            stop()
            return
        }
        if (user?.uid == u.uid) return
        stop()
        user = u
        _active.value = true
        bindersListener = store.collection(BINDERS)
            .whereArrayContains(MEMBER_IDS, u.uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    _problem.value = explain(error)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    _problem.value = null
                    scope.launch { lock.withLock { applyBinders(u, snap) } }
                }
            }
    }

    /** Stops syncing (signing out). What's on the phone stays. */
    fun stop() {
        bindersListener?.remove()
        bindersListener = null
        pageListeners.values.forEach { it.remove() }
        pageListeners.clear()
        coverListeners.values.forEach { it.remove() }
        coverListeners.clear()
        user = null
        _active.value = false
        _sharing.value = emptyMap()
        _problem.value = null
    }

    // ---- Online -> phone ----

    private suspend fun applyBinders(u: AppUser, snap: QuerySnapshot) {
        if (user?.uid != u.uid) return // signed out meanwhile
        val uid = u.uid
        val known = knownBinders(uid)
        val sharing = mutableMapOf<String, SharingInfo>()
        val remoteIds = mutableSetOf<String>()

        for (doc in snap.documents) {
            val remote = RemoteBinder.from(doc) ?: continue
            remoteIds += doc.id
            known += doc.id
            sharing[doc.id] = remote.sharing
            val local = binderDao.get(doc.id)
            val updated = remote.toBinder(doc.id, uid)
            if (local != updated) {
                binderDao.upsert(updated)
                // A different size: every page gets the right number of pockets.
                if (local != null && local.pocketsPerPage != updated.pocketsPerPage) fitPockets(doc.id, updated.pocketsPerPage)
            }
            watchPages(u, doc.id)
            watchCover(u, doc.id)
        }

        // Deleted online, or the owner stopped sharing it with you.
        for (change in snap.documentChanges) {
            if (change.type == DocumentChange.Type.REMOVED && change.document.id !in remoteIds) {
                removeLocal(change.document.id)
                known -= change.document.id
            }
        }

        // Only trust "it isn't online" when the answer came from the server,
        // not from the phone's offline copy.
        if (!snap.metadata.isFromCache) {
            for (b in binderDao.bindersOf(uid)) {
                if (b.id in remoteIds) continue
                if (b.id in known) {
                    // It was online before, so it has been deleted since.
                    if (System.currentTimeMillis() - b.createdAt > RECENT_MS) {
                        removeLocal(b.id)
                        known -= b.id
                    }
                } else if (b.isOwner) {
                    // Made on this phone and never sent: send it now, with its pages.
                    uploadWholeBinder(u, b)
                    known += b.id
                }
            }
        }

        (pageListeners.keys - remoteIds).forEach { pageListeners.remove(it)?.remove() }
        (coverListeners.keys - remoteIds).forEach { coverListeners.remove(it)?.remove() }
        saveKnownBinders(uid, known)
        _sharing.value = sharing
    }

    private fun watchPages(u: AppUser, binderId: String) {
        if (binderId in pageListeners) return
        pageListeners[binderId] = binderRef(binderId).collection(PAGES).addSnapshotListener { snap, error ->
            if (error != null) {
                // Usually: you were just removed from this binder.
                pageListeners.remove(binderId)?.remove()
                return@addSnapshotListener
            }
            if (snap != null) scope.launch { lock.withLock { applyPages(u, binderId, snap) } }
        }
    }

    private suspend fun applyPages(u: AppUser, binderId: String, snap: QuerySnapshot) {
        if (user?.uid != u.uid) return
        val binder = binderDao.get(binderId) ?: return
        val count = binder.pocketsPerPage
        for (change in snap.documentChanges) {
            val doc = change.document
            if (change.type == DocumentChange.Type.REMOVED) {
                deleteLocalPage(doc.id)
                continue
            }
            if (pageDao.getPage(doc.id) == null) {
                val createdAt = doc.getLong(CREATED_AT) ?: System.currentTimeMillis()
                pageDao.insertPage(Page(id = doc.id, binderId = binderId, photoPath = null, createdAt = createdAt))
            }
            val online = doc.get(POCKETS) as? Map<*, *>
            val onPhone = pageDao.pocketsOf(doc.id).associateBy { it.position }
            for (position in 0 until count) {
                val p = online?.get(position.toString()) as? Map<*, *>
                val pocket = Pocket(doc.id, position, cardId = p?.get("c") as? String, isEmpty = p?.get("e") == true)
                if (onPhone[position] != pocket) pageDao.upsertPocket(pocket)
            }
            if (onPhone.keys.any { it >= count }) pageDao.deletePocketsFrom(doc.id, count)
        }
    }

    private fun watchCover(u: AppUser, binderId: String) {
        if (covers == null || binderId in coverListeners) return
        coverListeners[binderId] = coverRef(binderId).addSnapshotListener { snap, error ->
            if (error != null) {
                // Usually: removed from this binder, or the new rules aren't published yet.
                coverListeners.remove(binderId)?.remove()
                return@addSnapshotListener
            }
            if (snap != null) scope.launch { lock.withLock { applyCover(u, binderId, snap) } }
        }
    }

    /**
     * The online cover changed (or we just started watching it). The owner's
     * cover wins on every phone. On the owner's own phone, a cover that was
     * never sent (set before covers went online) is sent now.
     */
    private suspend fun applyCover(u: AppUser, binderId: String, snap: DocumentSnapshot) {
        val covers = covers ?: return
        if (user?.uid != u.uid) return
        val binder = binderDao.get(binderId) ?: return
        val mine = covers.all.value[binderId]
        if (snap.exists()) {
            val version = snap.getLong(COVER_VERSION) ?: 0L
            if (mine != null && mine.version == version) {
                covers.markOnline(binderId)
                return
            }
            // The owner changed it on this phone and it hasn't gone up yet.
            if (binder.isOwner && mine != null && !covers.isOnline(binderId) && mine.version > version) {
                sendCover(binderId)
                return
            }
            val jpeg = snap.getBlob(COVER_JPEG)?.toBytes() ?: return
            val placement = CoverPlacement(
                x = snap.getDouble("x")?.toFloat() ?: 0.5f,
                y = snap.getDouble("y")?.toFloat() ?: 0.5f,
                zoom = snap.getDouble("zoom")?.toFloat() ?: 1f,
            )
            covers.applyOnline(binderId, jpeg, placement, version)
        } else {
            // Only trust "there's no cover" when the server says so.
            if (snap.metadata.isFromCache || mine == null) return
            if (binder.isOwner && !covers.isOnline(binderId)) {
                sendCover(binderId)
            } else {
                // The owner took it off (or never set one): plain leather here too.
                covers.remove(binderId)
            }
        }
    }

    /** Sends this phone's cover for [binderId] online (owners only). */
    private suspend fun sendCover(binderId: String) {
        val covers = covers ?: return
        val u = user ?: return
        val mine = covers.all.value[binderId] ?: return
        val key = "$binderId:${mine.version}"
        if (!coversSent.add(key)) return
        val jpeg = covers.onlineCopy(binderId) ?: return
        val doc = mapOf(
            COVER_JPEG to Blob.fromBytes(jpeg),
            "x" to mine.placement.x.toDouble(),
            "y" to mine.placement.y.toDouble(),
            "zoom" to mine.placement.zoom.toDouble(),
            COVER_VERSION to mine.version,
            "by" to u.uid,
        )
        coverRef(binderId).set(doc)
            .addOnSuccessListener { covers.markOnline(binderId) }
            .addOnFailureListener(::reportWriteFailure)
    }

    /** The owner saved or removed a cover on this phone: send it online. */
    fun coverChanged(binderId: String) {
        val covers = covers ?: return
        if (user == null) return
        scope.launch {
            lock.withLock {
                val binder = binderDao.get(binderId)?.takeIf { it.isOwner } ?: return@withLock
                if (covers.all.value[binderId] != null) {
                    sendCover(binder.id)
                } else {
                    coverRef(binder.id).delete().addOnFailureListener(::reportWriteFailure)
                }
            }
        }
    }

    /** After a binder changes size online: trims or adds pockets on every page. */
    private suspend fun fitPockets(binderId: String, count: Int) {
        for (page in pageDao.pagesOf(binderId)) {
            pageDao.deletePocketsFrom(page.id, count)
            pageDao.insertPocketsIfMissing((0 until count).map { Pocket(page.id, it) })
        }
    }

    private suspend fun removeLocal(binderId: String) {
        pageListeners.remove(binderId)?.remove()
        coverListeners.remove(binderId)?.remove()
        val binder = binderDao.get(binderId) ?: return
        pageDao.pagesOf(binderId).forEach { deleteLocalPage(it.id) }
        binderDao.delete(binder)
        covers?.remove(binderId)
    }

    private suspend fun deleteLocalPage(pageId: String) {
        val page = pageDao.getPage(pageId) ?: return
        page.photoPath?.let { File(it).delete() }
        pageDao.deletePage(pageId)
    }

    // ---- Phone -> online ----

    /** Sends a whole binder and all its pages (first time online, or after a resize). */
    private suspend fun uploadWholeBinder(u: AppUser, b: Binder, firstTime: Boolean = true) {
        val ref = binderRef(b.id)
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        if (firstTime) {
            val doc = newBinderDoc(u, b)
            writes.add { batch -> batch.set(ref, doc) }
        } else {
            val doc = settingsDoc(b)
            writes.add { batch -> batch.set(ref, doc, SetOptions.merge()) }
        }
        for (page in pageDao.pagesOf(b.id)) {
            val doc = pageDoc(page.createdAt, pageDao.pocketsOf(page.id))
            writes.add { batch -> batch.set(ref.collection(PAGES).document(page.id), doc) }
        }
        commit(writes)
    }

    /** Sends a binder made just now. */
    fun binderCreated(b: Binder) {
        val u = user ?: return
        scope.launch {
            lock.withLock {
                uploadWholeBinder(u, b)
                saveKnownBinders(u.uid, knownBinders(u.uid).apply { add(b.id) })
            }
        }
    }

    /** Name or colour changed (owners only). */
    fun binderSettingsChanged(b: Binder) {
        if (user == null || !b.isOwner) return
        binderRef(b.id).set(settingsDoc(b), SetOptions.merge()).addOnFailureListener(::reportWriteFailure)
    }

    /** After Edit binder changed the size: send the binder and every page again. */
    fun binderResized(binderId: String) {
        val u = user ?: return
        scope.launch {
            lock.withLock {
                val b = binderDao.get(binderId) ?: return@withLock
                if (b.isOwner) uploadWholeBinder(u, b, firstTime = false)
            }
        }
    }

    /**
     * The binder was deleted on this phone. The owner deletes it online for
     * everyone; anyone else just leaves it (it stays on the owner's shelf).
     * Call with the page IDs from before the pages were deleted.
     */
    fun binderRemoved(b: Binder, pageIds: List<String>) {
        val u = user ?: return
        val ref = binderRef(b.id)
        if (b.isOwner) {
            // Sent on its own, so the binder is still deleted if this one is refused.
            coverRef(b.id).delete()
            val writes = mutableListOf<(WriteBatch) -> Unit>()
            pageIds.forEach { id -> writes.add { batch -> batch.delete(ref.collection(PAGES).document(id)) } }
            _sharing.value[b.id]?.invites?.values?.forEach { inv ->
                writes.add { batch -> batch.delete(store.collection(INVITES).document(inv.code)) }
            }
            writes.add { batch -> batch.delete(ref) }
            commit(writes)
        } else {
            ref.update(leaveFields(u.uid)).addOnFailureListener(::reportWriteFailure)
        }
        scope.launch { lock.withLock { saveKnownBinders(u.uid, knownBinders(u.uid).apply { remove(b.id) }) } }
    }

    fun pagesAdded(binderId: String, pages: List<Page>) {
        if (user == null) return
        val ref = binderRef(binderId)
        val writes = mutableListOf<(WriteBatch) -> Unit>()
        pages.forEach { page ->
            val doc = pageDoc(page.createdAt, emptyList())
            writes.add { batch -> batch.set(ref.collection(PAGES).document(page.id), doc) }
        }
        commit(writes)
    }

    fun pageDeleted(binderId: String, pageId: String) {
        if (user == null) return
        binderRef(binderId).collection(PAGES).document(pageId).delete().addOnFailureListener(::reportWriteFailure)
    }

    /** One pocket changed: only that pocket is sent, so two people can fill different pockets at once. */
    fun pocketChanged(binderId: String, pocket: Pocket) {
        if (user == null) return
        val change = mapOf(POCKETS to mapOf(pocket.position.toString() to pocketDoc(pocket)))
        binderRef(binderId).collection(PAGES).document(pocket.pageId).set(change, SetOptions.merge())
            .addOnFailureListener(::reportWriteFailure)
    }

    /** Binders restored from a backup, or moved from "phone only": send any that aren't online yet. */
    fun uploadMissing() {
        val u = user ?: return
        scope.launch {
            lock.withLock {
                val known = knownBinders(u.uid)
                binderDao.bindersOf(u.uid).filter { it.isOwner && it.id !in known }.forEach {
                    uploadWholeBinder(u, it)
                    known += it.id
                }
                saveKnownBinders(u.uid, known)
            }
        }
    }

    // ---- Sharing ----

    /**
     * Makes a new share code for [binderId] that lets people join as [role]
     * (editor or viewer), replacing the old code for that role. Returns the
     * code, or null if online saving is off.
     */
    suspend fun createInvite(binderId: String, role: String): String? {
        val u = user ?: return null
        val b = binderDao.get(binderId)?.takeIf { it.isOwner } ?: return null
        val code = InviteCodes.newCode()
        val expires = Timestamp(Date(System.currentTimeMillis() + INVITE_DAYS * DAY_MS))
        val batch = store.batch()
        _sharing.value[binderId]?.invites?.get(role)?.let { old -> batch.delete(store.collection(INVITES).document(old.code)) }
        batch.set(
            store.collection(INVITES).document(code),
            mapOf(
                "binderId" to binderId,
                "role" to role,
                "createdBy" to u.uid,
                "binderName" to b.name,
                "ownerName" to displayName(u),
                "columns" to b.columns,
                "rows" to b.rows,
                "expiresAt" to expires,
            ),
        )
        batch.set(binderRef(binderId), mapOf(INVITES to mapOf(role to mapOf("code" to code, "expiresAt" to expires))), SetOptions.merge())
        batch.commit().addOnFailureListener(::reportWriteFailure)
        return code
    }

    /** Looks up a share code a friend scanned or typed. Null if there's no such code. */
    suspend fun lookupInvite(code: String): InvitePreview? {
        val doc = withTimeout(NETWORK_TIMEOUT_MS) { store.collection(INVITES).document(code).get().await() }
        if (!doc.exists()) return null
        val expires = doc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L
        return InvitePreview(
            code = code,
            binderId = doc.getString("binderId") ?: return null,
            binderName = doc.getString("binderName") ?: "A binder",
            ownerName = doc.getString("ownerName") ?: "A friend",
            role = doc.getString("role") ?: Binder.ROLE_VIEWER,
            columns = doc.getLong("columns")?.toInt() ?: 3,
            rows = doc.getLong("rows")?.toInt() ?: 3,
            expired = expires < System.currentTimeMillis(),
        )
    }

    /** Joins the binder a share code leads to. It then appears on the shelf by itself. */
    suspend fun join(invite: InvitePreview) {
        val u = user ?: error("Not signed in")
        withTimeout(NETWORK_TIMEOUT_MS) {
            binderRef(invite.binderId).update(
                mapOf(
                    MEMBER_IDS to FieldValue.arrayUnion(u.uid),
                    "$ROLES.${u.uid}" to invite.role,
                    "$MEMBER_NAMES.${u.uid}" to displayName(u),
                    "joinCode" to invite.code,
                ),
            ).await()
        }
    }

    /** The owner changes what someone can do. */
    fun setRole(binderId: String, uid: String, role: String) {
        if (user == null) return
        binderRef(binderId).update("$ROLES.$uid", role).addOnFailureListener(::reportWriteFailure)
    }

    /** The owner removes someone from a binder. */
    fun removeMember(binderId: String, uid: String) {
        if (user == null) return
        binderRef(binderId).update(leaveFields(uid)).addOnFailureListener(::reportWriteFailure)
    }

    /** The owner stops sharing: everyone else is removed and the share codes stop working. */
    fun stopSharing(binderId: String) {
        val u = user ?: return
        val batch = store.batch()
        _sharing.value[binderId]?.invites?.values?.forEach { batch.delete(store.collection(INVITES).document(it.code)) }
        batch.update(
            binderRef(binderId),
            mapOf(
                MEMBER_IDS to listOf(u.uid),
                ROLES to mapOf(u.uid to Binder.ROLE_OWNER),
                MEMBER_NAMES to mapOf(u.uid to displayName(u)),
                INVITES to FieldValue.delete(),
            ),
        )
        batch.commit().addOnFailureListener(::reportWriteFailure)
    }

    // ---- Helpers ----

    private fun binderRef(id: String): DocumentReference = store.collection(BINDERS).document(id)

    private fun coverRef(binderId: String): DocumentReference = binderRef(binderId).collection(COVER).document(COVER_DOC)

    private fun commit(writes: List<(WriteBatch) -> Unit>) {
        // Firestore takes at most 500 changes at once.
        writes.chunked(400).forEach { chunk ->
            val batch = store.batch()
            chunk.forEach { it(batch) }
            batch.commit().addOnFailureListener(::reportWriteFailure)
        }
    }

    private fun reportWriteFailure(e: Exception) {
        _problem.value = explain(e)
    }

    private fun newBinderDoc(u: AppUser, b: Binder): Map<String, Any> = settingsDoc(b) + mapOf(
        "ownerId" to u.uid,
        CREATED_AT to b.createdAt,
        MEMBER_IDS to listOf(u.uid),
        ROLES to mapOf(u.uid to Binder.ROLE_OWNER),
        MEMBER_NAMES to mapOf(u.uid to displayName(u)),
    )

    private fun settingsDoc(b: Binder): Map<String, Any> = mapOf(
        "name" to b.name,
        "colorHex" to b.colorHex,
        "columns" to b.columns,
        "rows" to b.rows,
        // Missing cards: which expansions the binder collects, and main or master set.
        "followedSets" to b.followedSets,
        "countSecretRares" to b.countSecretRares,
    )

    private fun pageDoc(createdAt: Long, pockets: List<Pocket>): Map<String, Any> = mapOf(
        CREATED_AT to createdAt,
        POCKETS to pockets.filter { it.cardId != null || it.isEmpty }.associate { it.position.toString() to pocketDoc(it) },
    )

    // "c" = which card, "e" = marked as an empty pocket. Short names keep the data small.
    private fun pocketDoc(p: Pocket): Map<String, Any?> = mapOf("c" to p.cardId, "e" to p.isEmpty)

    private fun leaveFields(uid: String): Map<String, Any> = mapOf(
        MEMBER_IDS to FieldValue.arrayRemove(uid),
        "$ROLES.$uid" to FieldValue.delete(),
        "$MEMBER_NAMES.$uid" to FieldValue.delete(),
    )

    private fun displayName(u: AppUser): String =
        u.name?.takeIf { it.isNotBlank() } ?: u.email?.substringBefore('@') ?: "A friend"

    // Binders this phone has seen online for this person, so we can tell
    // "deleted online" apart from "made on this phone, not sent yet".
    private fun knownBinders(uid: String): MutableSet<String> =
        prefs.getStringSet("known_$uid", emptySet()).orEmpty().toMutableSet()

    private fun saveKnownBinders(uid: String, ids: Set<String>) {
        prefs.edit { putStringSet("known_$uid", ids.toSet()) }
    }

    private fun explain(e: Exception): String = when ((e as? FirebaseFirestoreException)?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Online saving is blocked. The database rules in Firebase need updating (see “Shared binders” in the README)."
        FirebaseFirestoreException.Code.NOT_FOUND, FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
            "The online database isn't switched on yet in Firebase (see “Shared binders” in the README)."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "You're offline. Changes are kept on this phone and go online later."
        else -> "Online saving had a problem: ${e.message ?: "unknown"}"
    }

    /** A binder as stored online. */
    private class RemoteBinder(
        val name: String,
        val colorHex: String,
        val columns: Int,
        val rows: Int,
        val createdAt: Long,
        val ownerId: String,
        val memberIds: List<String>,
        val roles: Map<String, String>,
        val names: Map<String, String>,
        val invites: Map<String, InviteCode>,
        val followedSets: String,
        val countSecretRares: Boolean,
    ) {
        val sharing: SharingInfo
            get() = SharingInfo(
                ownerId = ownerId,
                members = memberIds.map { uid -> Member(uid, names[uid] ?: "Someone", roles[uid] ?: Binder.ROLE_VIEWER) }
                    .sortedWith(compareBy({ it.role != Binder.ROLE_OWNER }, { it.name.lowercase() })),
                invites = invites,
            )

        fun toBinder(id: String, uid: String) = Binder(
            id = id,
            ownerId = uid,
            name = name,
            colorHex = colorHex,
            columns = columns,
            rows = rows,
            createdAt = createdAt,
            role = if (ownerId == uid) Binder.ROLE_OWNER else roles[uid] ?: Binder.ROLE_VIEWER,
            ownerName = if (ownerId == uid) null else names[ownerId],
            memberCount = memberIds.size.coerceAtLeast(1),
            followedSets = followedSets,
            countSecretRares = countSecretRares,
        )

        companion object {
            fun from(doc: DocumentSnapshot): RemoteBinder? {
                val owner = doc.getString("ownerId") ?: return null
                val invites = (doc.get(INVITES) as? Map<*, *>).orEmpty().mapNotNull { (role, value) ->
                    val m = value as? Map<*, *> ?: return@mapNotNull null
                    val code = m["code"] as? String ?: return@mapNotNull null
                    val expires = (m["expiresAt"] as? Timestamp)?.toDate()?.time ?: 0L
                    val r = role as? String ?: return@mapNotNull null
                    r to InviteCode(code, r, expires)
                }.toMap()
                return RemoteBinder(
                    name = doc.getString("name") ?: "Binder",
                    colorHex = doc.getString("colorHex") ?: "#1F3A5F",
                    columns = doc.getLong("columns")?.toInt() ?: 3,
                    rows = doc.getLong("rows")?.toInt() ?: 3,
                    createdAt = doc.getLong(CREATED_AT) ?: 0L,
                    ownerId = owner,
                    memberIds = (doc.get(MEMBER_IDS) as? List<*>).orEmpty().filterIsInstance<String>(),
                    roles = stringMap(doc.get(ROLES)),
                    names = stringMap(doc.get(MEMBER_NAMES)),
                    invites = invites,
                    followedSets = doc.getString("followedSets").orEmpty(),
                    countSecretRares = doc.getBoolean("countSecretRares") ?: false,
                )
            }

            private fun stringMap(value: Any?): Map<String, String> =
                (value as? Map<*, *>).orEmpty().mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }.toMap()
        }
    }

    private companion object {
        const val BINDERS = "binders"
        const val PAGES = "pages"
        const val COVER = "cover"
        const val COVER_DOC = "picture"
        const val COVER_JPEG = "jpeg"
        const val COVER_VERSION = "version"
        const val INVITES = "invites"
        const val MEMBER_IDS = "memberIds"
        const val ROLES = "roles"
        const val MEMBER_NAMES = "memberNames"
        const val POCKETS = "pockets"
        const val CREATED_AT = "createdAt"
        const val INVITE_DAYS = 7L
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val NETWORK_TIMEOUT_MS = 15_000L
        // A binder made in the last 2 minutes may not have reached the server yet.
        const val RECENT_MS = 2 * 60 * 1000L
    }
}
