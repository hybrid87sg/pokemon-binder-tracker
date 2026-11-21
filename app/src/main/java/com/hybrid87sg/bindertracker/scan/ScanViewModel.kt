package com.hybrid87sg.bindertracker.scan

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hybrid87sg.bindertracker.data.AppDatabase
import com.hybrid87sg.bindertracker.data.Binder
import com.hybrid87sg.bindertracker.data.BinderPage
import com.hybrid87sg.bindertracker.data.Pocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The steps of scanning a page, in order. */
sealed interface ScanStep {
    /** Camera is showing; waiting for the person to take the photo. */
    data object Camera : ScanStep

    /** Photo taken; reading the cards. */
    data object Reading : ScanStep

    /** Showing what was found, so the person can check and fix it before saving. */
    data class Review(val pockets: List<PocketDraft>) : ScanStep

    /** Something went wrong; [message] says what and what to do. */
    data class Failed(val message: String) : ScanStep
}

/**
 * The "brain" of the scan screen. The screen shows whichever [ScanStep] we
 * are on; this class moves between steps and saves the page at the end.
 *
 * The binder's ID comes from the screen's address ("binder/{id}/scan"),
 * which Android hands us in [saved].
 */
class ScanViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val binderId: String = checkNotNull(saved["id"]) { "Scan screen opened without a binder" }
    private val db = AppDatabase.get(app)
    private val reader = PageReader()
    private val lookup: CardLookup = CardLookups.create(app)

    val binder: Flow<Binder?> = db.binderDao().observeBinder(binderId)

    private val _step = MutableStateFlow<ScanStep>(ScanStep.Camera)
    val step: StateFlow<ScanStep> = _step.asStateFlow()

    /** Called by the camera with the photo it just took. */
    fun onPhotoTaken(photo: Bitmap, rotationDegrees: Int, guide: GuideFrame, binder: Binder) {
        _step.value = ScanStep.Reading
        viewModelScope.launch {
            try {
                val read = reader.read(photo, rotationDegrees, guide, binder.columns, binder.rows)
                val drafts = read.map { card ->
                    val candidates = if (card.looksEmpty) emptyList() else runCatching {
                        lookup.find(card.name, card.number, card.setTotal)
                    }.getOrDefault(emptyList())
                    PocketDraft.from(card, candidates)
                }
                _step.value = ScanStep.Review(drafts)
            } catch (e: Exception) {
                _step.value = ScanStep.Failed("Couldn't read the photo. Hold the phone steady over the page and try again.")
            }
        }
    }

    fun onPhotoFailed() {
        _step.value = ScanStep.Failed("The camera couldn't take the photo. Try again.")
    }

    /** Back to the camera to take the photo again. */
    fun retake() {
        _step.value = ScanStep.Camera
    }

    /** The person picked a different card for a pocket (or null to mark it empty). */
    fun choose(position: Int, card: CardCandidate?) = editPocket(position) {
        if (card == null) it.copy(chosen = null, empty = true)
        else it.copy(chosen = card, empty = false, candidates = listOf(card) + it.candidates.filter { c -> c != card })
    }

    /** The person typed a name for a pocket the card index couldn't match. */
    fun nameByHand(position: Int, name: String) = editPocket(position) {
        it.copy(read = it.read.copy(name = name.trim()), chosen = null, empty = false)
    }

    /** Searches the card index for text the person typed while fixing a pocket. */
    suspend fun search(query: String): List<CardCandidate> =
        if (query.isBlank()) emptyList() else runCatching { lookup.search(query.trim()) }.getOrDefault(emptyList())

    private fun editPocket(position: Int, change: (PocketDraft) -> PocketDraft) {
        _step.update { step ->
            if (step !is ScanStep.Review) step
            else step.copy(pockets = step.pockets.map { if (it.position == position) change(it) else it })
        }
    }

    /** Saves the page as the binder's next page, then calls [onSaved]. */
    fun save(onSaved: () -> Unit) {
        val review = _step.value as? ScanStep.Review ?: return
        viewModelScope.launch {
            val dao = db.pageDao()
            val page = BinderPage(binderId = binderId, pageNumber = dao.lastPageNumber(binderId) + 1)
            val pockets = review.pockets.map { d ->
                Pocket(
                    pageId = page.id,
                    position = d.position,
                    cardId = if (d.empty) null else d.chosen?.cardId,
                    name = d.displayName,
                    setName = if (d.empty) null else d.chosen?.setName,
                    number = d.displayNumber,
                    readText = d.read.text,
                )
            }
            dao.savePage(page, pockets)
            onSaved()
        }
    }

    override fun onCleared() {
        reader.close()
    }
}
