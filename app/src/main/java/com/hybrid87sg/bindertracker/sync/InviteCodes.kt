package com.hybrid87sg.bindertracker.sync

import java.security.SecureRandom
import java.util.Random

/**
 * Share codes like "7Q4K-M2PX", which a friend scans (as a QR code) or types
 * to join a shared binder.
 *
 * The letters leave out ones that are easy to mix up when typing (I, L, O,
 * 0 and 1). Eight of them give about 850 billion possible codes, so nobody
 * can guess one.
 */
object InviteCodes {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 8

    /** What a QR code holds: a link the app understands. */
    private const val LINK_START = "bindertracker://join/"

    fun newCode(random: Random = SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** "7Q4KM2PX" shown as "7Q4K-M2PX", easier to read out. */
    fun pretty(code: String): String = if (code.length == LENGTH) code.take(4) + "-" + code.drop(4) else code

    fun link(code: String): String = LINK_START + code

    /**
     * Turns what was scanned or typed into a code, or null if it isn't one.
     * Accepts "7q4k-m2px", "7Q4K M2PX" or the QR code's link.
     */
    fun parse(text: String): String? {
        val raw = text.trim().substringAfterLast("join/")
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
        return cleaned.takeIf { it.length == LENGTH && it.all { c -> c in ALPHABET } }
    }
}
