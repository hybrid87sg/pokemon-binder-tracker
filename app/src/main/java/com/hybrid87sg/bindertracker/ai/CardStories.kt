package com.hybrid87sg.bindertracker.ai

import android.content.Context
import com.hybrid87sg.bindertracker.data.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Card stories: a short write-up about one card (its artist, its set and
 * era, and why collectors like it), written by Gemini.
 *
 * Nothing is sent until you ask. Each story is saved in a small text file
 * on the phone (filesDir/card-stories), so it opens instantly next time and
 * works without internet. "Write it again" replaces the saved one.
 */
object CardStories {
    private const val FOLDER = "card-stories"

    private fun file(context: Context, cardId: String): File {
        // Card IDs look like "sv03.5-025"; keep only safe characters for a file name.
        val safe = cardId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(File(context.filesDir, FOLDER), "$safe.txt")
    }

    /** The saved story for this card, or null if it hasn't been written yet. */
    suspend fun saved(context: Context, cardId: String): String? = withContext(Dispatchers.IO) {
        file(context, cardId).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }

    /** Asks Gemini for a story, saves it and returns it. */
    suspend fun write(
        context: Context,
        card: Card,
        rarity: String?,
        illustrator: String?,
    ): Result<String> {
        if (!AiSetup.isAvailable(context)) return Result.failure(IllegalStateException("Firebase isn't connected in this build."))
        val facts = buildList {
            add("Name: ${card.name}")
            add("Expansion: ${card.setName}" + if (card.seriesName.isNotEmpty()) " (${card.seriesName} series)" else "")
            add("Number: ${card.number}")
            rarity?.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }?.let { add("Rarity: $it") }
                ?: card.rarity.takeIf { it.isNotBlank() }?.let { add("Rarity: $it") }
            illustrator?.takeIf { it.isNotBlank() }?.let { add("Illustrator: $it") }
        }.joinToString("\n")
        val prompt = """
            You write short, friendly notes for a Pokémon TCG collector's binder app.
            Write about this card in 3 short paragraphs (2 or 3 sentences each), in plain everyday English:
            1. The card itself and the artwork (mention the illustrator by name if given).
            2. The expansion it comes from and when that set came out.
            3. Why collectors like it or look for it.
            Rules: no headings, no lists, no markdown, no prices or money amounts.
            Only state facts you are confident about; if unsure, keep it general.

            $facts
        """.trimIndent()
        return runCatching {
            val text = AiSetup.model.generateContent(prompt).text?.let(::plain).orEmpty()
            if (text.isBlank()) error("Gemini answered, but with no words. Try again.")
            withContext(Dispatchers.IO) {
                file(context, card.id).apply { parentFile?.mkdirs() }.writeText(text)
            }
            text
        }.recoverCatching { e -> throw IllegalStateException(AiSetup.explain(e), e) }
    }

    /** Removes markdown marks (bold stars, headings) so the text reads cleanly. */
    internal fun plain(text: String): String = text
        .replace("**", "")
        .replace(Regex("(?m)^#+\\s*"), "")
        .replace(Regex("(?m)^\\s*[*-]\\s+"), "• ")
        .trim()
}
