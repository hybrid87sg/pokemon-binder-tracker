package com.hybrid87sg.bindertracker.sync

import com.google.zxing.common.reedsolomon.GenericGF
import com.google.zxing.common.reedsolomon.ReedSolomonDecoder
import com.google.zxing.common.reedsolomon.ReedSolomonEncoder
import com.google.zxing.common.reedsolomon.ReedSolomonException

/**
 * The binder ring code: our own round share code. It holds the same 8 letter
 * share code as the typed code (see [InviteCodes]), drawn as white arcs on
 * three rings around the app logo.
 *
 * How it's laid out (sizes are parts of the disc's radius, which is 1):
 * - Each ring has 41 positions going clockwise from the top. Position 0 at
 *   the top always stays empty (a small triangle sits above it, pointing
 *   at it), and positions 1 to 40 are the ring's 40 slots.
 * - A filled slot (white) is a 1 and an empty slot (dark) is a 0. Slots next
 *   to each other join into one long arc.
 * - 120 slots in all: 48 hold the code and a version number, and 72 hold
 *   repair data (Reed-Solomon, the same maths QR codes use, done by the ZXing
 *   library). Up to 6 of the 20 six-slot pieces can be misread and the code
 *   still comes out right.
 *
 * The drawing (RingCodeImage) and the camera reader (RingCodeReader) both use
 * these numbers, so they always agree.
 */
object RingCode {
    const val RINGS = 3
    const val SLOTS = 40
    /** Positions round each ring: the empty top one plus the 40 slots. */
    const val POSITIONS = SLOTS + 1
    const val BITS = RINGS * SLOTS

    /** Ring centres, outside ring first. */
    val RING_RADII = floatArrayOf(166f / 196f, 141f / 196f, 116f / 196f)
    /** How thick each ring is. */
    const val RING_WIDTH = 13f / 196f
    /** The white circle in the middle with the logo. */
    const val CENTRE_RADIUS = 96f / 196f
    /** The white edge around the disc, so it stands out on any background. */
    const val HALO_RADIUS = 1.08f

    /** The design version, so later designs can tell old codes apart. */
    private const val VERSION = 1L
    private const val DATA_PIECES = 8
    private const val REPAIR_PIECES = 12
    private const val PIECE_BITS = 6
    private const val CODE_BITS = 40
    private val field: GenericGF = GenericGF.AZTEC_DATA_6

    /** The angle (radians, clockwise from the top) of the middle of slot [slot]. */
    fun slotAngle(slot: Int): Double = (slot + 1.5) * 2 * Math.PI / POSITIONS

    /** The 120 slots for [code] (an 8 letter share code), outside ring first. */
    fun encode(code: String): BooleanArray {
        require(code.length == InviteCodes.LENGTH && code.all { it in InviteCodes.ALPHABET }) { "Not a share code: $code" }
        var number = 0L
        for (c in code) number = number * InviteCodes.ALPHABET.length + InviteCodes.ALPHABET.indexOf(c)
        val value = (VERSION shl CODE_BITS) or number
        val pieces = IntArray(DATA_PIECES + REPAIR_PIECES)
        for (i in 0 until DATA_PIECES) {
            pieces[i] = ((value shr ((DATA_PIECES - 1 - i) * PIECE_BITS)) and 63L).toInt()
        }
        ReedSolomonEncoder(field).encode(pieces, REPAIR_PIECES)
        val bits = BooleanArray(BITS)
        for (i in pieces.indices) for (b in 0 until PIECE_BITS) {
            bits[i * PIECE_BITS + b] = (pieces[i] shr (PIECE_BITS - 1 - b)) and 1 == 1
        }
        return bits
    }

    /** The share code in [bits] (as read by the camera), or null if it can't be repaired. */
    fun decode(bits: BooleanArray): String? {
        if (bits.size != BITS) return null
        val pieces = IntArray(DATA_PIECES + REPAIR_PIECES) { i ->
            var p = 0
            for (b in 0 until PIECE_BITS) p = (p shl 1) or (if (bits[i * PIECE_BITS + b]) 1 else 0)
            p
        }
        try {
            ReedSolomonDecoder(field).decode(pieces, REPAIR_PIECES)
        } catch (_: ReedSolomonException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        var value = 0L
        for (i in 0 until DATA_PIECES) value = (value shl PIECE_BITS) or pieces[i].toLong()
        if (value shr CODE_BITS != VERSION) return null
        var number = value and ((1L shl CODE_BITS) - 1)
        val chars = CharArray(InviteCodes.LENGTH)
        for (i in chars.indices.reversed()) {
            chars[i] = InviteCodes.ALPHABET[(number % InviteCodes.ALPHABET.length).toInt()]
            number /= InviteCodes.ALPHABET.length
        }
        // A number too big for 8 letters means it wasn't really a code.
        return if (number == 0L) String(chars) else null
    }
}
