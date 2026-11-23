package com.hybrid87sg.bindertracker.ai

import android.content.Context

/** One line in Binder chat: something you asked, or Gemini's answer. */
data class ChatMessage(val fromMe: Boolean, val text: String, val failed: Boolean = false)

/**
 * Binder chat: questions about your own collection, answered by Gemini.
 *
 * Every question goes with a plain-text summary of your binders (what's in
 * each one, on which page, and what the collecting binders are still
 * missing), so the answers are about your cards rather than guesses.
 * The conversation is kept only while the app is open.
 */
object BinderChat {
    /** Starter questions shown above the box. */
    val suggestions = listOf(
        "Which binder is closest to finished?",
        "Where are my Pikachu cards?",
        "What am I still missing?",
        "Which expansion do I have the most cards from?",
    )

    private const val MAX_TURNS = 10

    suspend fun answer(context: Context, collection: String, history: List<ChatMessage>, question: String): Result<String> {
        if (!AiSetup.isAvailable(context)) return Result.failure(IllegalStateException("Firebase isn't connected in this build."))
        val earlier = history.filterNot { it.failed }.takeLast(MAX_TURNS).joinToString("\n") {
            (if (it.fromMe) "Collector: " else "You: ") + it.text
        }
        val prompt = buildString {
            appendLine("You are Binder chat, the helper inside a Pokémon TCG binder tracking app.")
            appendLine("Answer the collector's question using ONLY the collection below. Be short and friendly:")
            appendLine("at most 5 sentences, or a short list using • bullets. No markdown, no headings, no bold.")
            appendLine("When you mention a card, say which binder and page it is on if the collection says so.")
            appendLine("If the answer isn't in the collection, say so plainly. Don't make up prices or cards.")
            appendLine()
            appendLine("=== The collection ===")
            appendLine(collection)
            if (earlier.isNotEmpty()) {
                appendLine()
                appendLine("=== The conversation so far ===")
                appendLine(earlier)
            }
            appendLine()
            append("Collector: ").append(question)
        }
        return runCatching {
            AiSetup.model.generateContent(prompt).text?.let(CardStories::plain).orEmpty()
                .ifBlank { error("Gemini answered, but with no words. Try asking another way.") }
        }.recoverCatching { e -> throw aiFailure(e) }
    }
}
