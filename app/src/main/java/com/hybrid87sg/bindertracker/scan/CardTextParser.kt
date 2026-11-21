package com.hybrid87sg.bindertracker.scan

/**
 * One line of text the camera read, and where it was in the photo
 * (in pixels: left/top is the line's top-left corner).
 */
data class TextLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/**
 * What we could read from one pocket.
 *
 * @param position pocket number: 0 is top-left, counting left to right, row by row.
 * @param name the card's name as read, e.g. "Pikachu", or null if not found.
 * @param number the collector number before the slash, e.g. "025".
 * @param setTotal the number after the slash, e.g. 198 in "025/198".
 * @param text everything read in this pocket, one line per row.
 */
data class ReadCard(
    val position: Int,
    val name: String?,
    val number: String?,
    val setTotal: Int?,
    val text: String,
) {
    /** No text at all in the pocket: most likely an empty pocket. */
    val looksEmpty: Boolean get() = text.isBlank()

    /** "025/198", or just "025" when the total wasn't readable. */
    val printedNumber: String? get() = number?.let { n -> setTotal?.let { "$n/$it" } ?: n }
}

/**
 * Turns the raw text the camera read into names and numbers.
 *
 * This part is plain Kotlin with no camera involved, so it can be tested on
 * a computer (see CardTextParserTest).
 */
object CardTextParser {

    // A collector number like "025/198", "TG05/TG30", "SV107/SV122" or "136 / 189".
    // \b means "word edge", so we don't grab digits from the middle of other text.
    private val numberRegex = Regex("""\b([A-Z]{0,4}\d{1,3}[a-z]?)\s*/\s*([A-Z]{0,4})(\d{1,3})\b""")

    // Words printed above or next to the name that are not part of it.
    private val leadingNoise = Regex(
        """^(BASIC|STAGE\s*[12I]?|TRAINER|ITEM|SUPPORTER|STADIUM|POK[EÉ]MON\s+TOOL|SPECIAL\s+ENERGY|BASIC\s+ENERGY|RESTORED)\b[\s:.-]*""",
        RegexOption.IGNORE_CASE,
    )

    // Hit points printed after the name, e.g. "Pikachu HP 60" or "Pikachu 60HP".
    private val trailingHp = Regex("""(\s*HP\s*\d+|\s*\d+\s*HP|\s+\d{2,3})\s*$""", RegexOption.IGNORE_CASE)

    // Lines that are never a name.
    private val notAName = Regex("""^(evolves from|put .* on|illus\.?|weakness|resistance|retreat|ability)\b""", RegexOption.IGNORE_CASE)

    // The word "Pokémon" on its own (or "Pokémon Power") is a label, but
    // "Pokémon Catcher" is a real card name, so only the label is skipped.
    private val pokemonLabel = Regex("""^pok[eé]mon(\s+(power|body))?\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Sorts every line on the page into its pocket, by where the middle of
     * the line is. Returns one list of lines per pocket, in pocket order.
     */
    fun splitIntoPockets(
        lines: List<TextLine>,
        pageWidth: Int,
        pageHeight: Int,
        columns: Int,
        rows: Int,
    ): List<List<TextLine>> {
        val pockets = List(columns * rows) { mutableListOf<TextLine>() }
        if (pageWidth <= 0 || pageHeight <= 0) return pockets
        for (line in lines) {
            val col = (line.centerX * columns / pageWidth).coerceIn(0, columns - 1)
            val row = (line.centerY * rows / pageHeight).coerceIn(0, rows - 1)
            pockets[row * columns + col].add(line)
        }
        return pockets
    }

    /**
     * Reads one pocket's lines. [pocketTop] and [pocketHeight] say where the
     * pocket is on the page, so we can tell which lines are near its top
     * (where a card's name is printed).
     */
    fun readPocket(position: Int, lines: List<TextLine>, pocketTop: Int, pocketHeight: Int): ReadCard {
        val sorted = lines.sortedWith(compareBy({ it.top }, { it.left }))
        val numberMatch = findNumber(sorted)
        return ReadCard(
            position = position,
            name = findName(sorted, pocketTop, pocketHeight),
            number = numberMatch?.first,
            setTotal = numberMatch?.second,
            text = sorted.joinToString("\n") { it.text },
        )
    }

    /**
     * Finds the collector number. It's printed near the bottom of a card, so
     * we look from the bottom line upwards and take the first one we see.
     */
    fun findNumber(lines: List<TextLine>): Pair<String, Int>? {
        for (line in lines.sortedByDescending { it.top }) {
            val m = numberRegex.find(fixDigits(line.text)) ?: continue
            val total = m.groupValues[3].toIntOrNull() ?: continue
            return m.groupValues[1] to total
        }
        return null
    }

    /**
     * Finds the card's name: the first sensible line in the top part of the
     * pocket, with words like "BASIC" and the HP number removed.
     */
    fun findName(lines: List<TextLine>, pocketTop: Int, pocketHeight: Int): String? {
        val topLimit = pocketTop + pocketHeight * 0.35
        for (line in lines.sortedBy { it.top }) {
            if (line.top > topLimit) break
            val cleaned = cleanName(line.text) ?: continue
            return cleaned
        }
        return null
    }

    /** Tidies one line into a name, or returns null if it can't be a name. */
    fun cleanName(raw: String): String? {
        var text = raw.trim()
        // Remove "BASIC", "STAGE 1" etc. at the start (sometimes twice, e.g. "BASIC POKÉMON TOOL").
        repeat(2) { text = text.replace(leadingNoise, "").trim() }
        if (notAName.containsMatchIn(text) || pokemonLabel.matches(text)) return null
        text = text.replace(trailingHp, "").trim()
        // A name needs at least 3 letters (so stray marks like "~" or "V" alone are skipped).
        if (text.count { it.isLetter() } < 3) return null
        return text
    }

    /**
     * The camera sometimes mistakes a letter for a look-alike digit inside a
     * number (O for 0, l or I for 1). Fixes that only next to a slash and
     * other digits, so names are never changed.
     */
    private fun fixDigits(text: String): String =
        Regex("""[0-9OoIl]{1,3}\s*/\s*[0-9OoIl]{1,3}""").replace(text) { m ->
            m.value.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1')
        }
}
