package com.hybrid87sg.bindertracker.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Questions for the Missing cards page: which cards a binder holds, where,
 * and the full card lists of the expansions it collects.
 */
@Dao
interface ChecklistDao {
    /** Every pocket of a binder, page by page, in order (for "which pocket does it go in?"). */
    @Query(
        """
        SELECT pages.id AS pageId, pages.createdAt AS pageCreatedAt, pockets.position, pockets.cardId
        FROM pockets INNER JOIN pages ON pages.id = pockets.pageId
        WHERE pages.binderId = :binderId
        ORDER BY pages.createdAt, pages.id, pockets.position
        """,
    )
    fun observeBinderPockets(binderId: String): Flow<List<BinderPocket>>

    /** Every card in every binder, for the badges on the shelf. */
    @Query(
        """
        SELECT DISTINCT pages.binderId AS binderId, pockets.cardId AS cardId
        FROM pockets INNER JOIN pages ON pages.id = pockets.pageId
        WHERE pockets.cardId IS NOT NULL
        """,
    )
    fun observeOwnedCards(): Flow<List<OwnedCard>>

    /** Every card in these expansions (from the card index on the phone). */
    @Query("SELECT * FROM cards WHERE setId IN (:setIds)")
    suspend fun cardsInSets(setIds: List<String>): List<Card>

    /** How many cards the card index has; changes when it's downloaded again. */
    @Query("SELECT COUNT(*) FROM cards")
    fun observeCardCount(): Flow<Int>

    /** The expansion most of a binder's cards come from, with how many: for the suggestion. */
    @Query(
        """
        SELECT cards.setId AS setId, cards.setName AS setName, COUNT(*) AS cards
        FROM pockets INNER JOIN pages ON pages.id = pockets.pageId
        INNER JOIN cards ON cards.id = pockets.cardId
        WHERE pages.binderId = :binderId
        GROUP BY cards.setId ORDER BY cards DESC LIMIT 1
        """,
    )
    suspend fun topExpansion(binderId: String): ExpansionCount?
}

/** One pocket of a binder: which page, which spot on it, and the card in it (if any). */
data class BinderPocket(val pageId: String, val pageCreatedAt: Long, val position: Int, val cardId: String?)

/** "Binder X holds card Y." */
data class OwnedCard(val binderId: String, val cardId: String)

/** "12 cards from 151." */
data class ExpansionCount(val setId: String, val setName: String, val cards: Int)
