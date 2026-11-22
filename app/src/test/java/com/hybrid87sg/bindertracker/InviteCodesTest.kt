package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.sync.InviteCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** Share codes: made, shown and read back the same way. */
class InviteCodesTest {
    @Test
    fun newCodesAreEightEasyCharacters() {
        val random = Random(42)
        repeat(200) {
            val code = InviteCodes.newCode(random)
            assertEquals(8, code.length)
            assertTrue(code, code.none { it in "ILO01" })
            assertEquals(code, InviteCodes.parse(code))
        }
    }

    @Test
    fun readsTypedCodesAndQrLinks() {
        assertEquals("7Q4KM2PX", InviteCodes.parse("7q4k-m2px"))
        assertEquals("7Q4KM2PX", InviteCodes.parse(" 7Q4K M2PX "))
        assertEquals("7Q4KM2PX", InviteCodes.parse(InviteCodes.link("7Q4KM2PX")))
        assertEquals("7Q4K-M2PX", InviteCodes.pretty("7Q4KM2PX"))
    }

    @Test
    fun rejectsThingsThatAreNotCodes() {
        assertNull(InviteCodes.parse(""))
        assertNull(InviteCodes.parse("7Q4K"))
        assertNull(InviteCodes.parse("https://example.com"))
        // "O" and "1" are never used in codes.
        assertNull(InviteCodes.parse("7Q4KO21X"))
    }
}
