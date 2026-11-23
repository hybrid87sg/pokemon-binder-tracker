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

    /**
     * Where every card in every binder is: the binder's name and the page
     * number (pages are numbered in the order they were added). For search,
     * which lists your own cards first and says where they are.
     */
    @Query(
        """
        SELECT pockets.cardId AS cardId, binders.name AS binderName,
          (SELECT COUNT(*) FROM pages p2 WHERE p2.binderId = pages.binderId AND p2.createdAt <= pages.createdAt) AS pageNumber
        FROM pockets INNER JOIN pages ON pages.id = pockets.pageId
        INNER JOIN binders ON binders.id = pages.binderId
        WHERE pockets.cardId IS NOT NULL
        ORDER BY binders.createdAt, pages.createdAt, pockets.position
        """,
    )
    suspend fun cardPlaces(): List<CardPlace>

    /** Every card in these expansions (from the card index on the phone). */
    @Query("SELECT * FROM cards WHERE setId IN (:setIds)")
    suspend fun cardsInSets(setIds: List<String>): List<Card>

    /** How many cards the card index has; changes when it's downloaded again. */
    @Query("SELECT COUNT(*) FROM cards")
    fun observeCardCount(): Flow<Int>

    /** Every card in any binder on this phone, with its TCGdex picture address (for offline artwork). */
    @Query(
        """
        SELECT DISTINCT pockets.cardId AS id, cards.imageUrl AS imageUrl
        FROM pockets LEFT JOIN cards ON cards.id = pockets.cardId
        WHERE pockets.cardId IS NOT NULL
        """,
    )
    fun observeBinderCardArt(): Flow<List<CardArt>>

    /** The expansions each binder on this phone collects, e.g. "sv01,sv03.5" (for offline artwork). */
    @Query("SELECT followedSets FROM binders")
    fun observeFollowedSets(): Flow<List<String>>

    /** Every card in these expansions, with its picture address (for offline artwork). */
    @Query("SELECT id, imageUrl FROM cards WHERE setId IN (:setIds)")
    suspend fun cardArtInSets(setIds: List<String>): List<CardArt>

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

/** "Card Y is on page 3 of the binder called X." */
data class CardPlace(val cardId: String, val binderName: String, val pageNumber: Int)

/** "Binder X holds card Y." */
data class OwnedCard(val binderId: String, val cardId: String)

/** "12 cards from 151." */
data class ExpansionCount(val setId: String, val setName: String, val cards: Int)

/** A card and where its TCGdex picture lives (null if TCGdex has none, or the card list hasn't got it). */
data class CardArt(val id: String, val imageUrl: String?)
