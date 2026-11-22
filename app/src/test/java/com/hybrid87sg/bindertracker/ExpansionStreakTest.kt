package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.scan.ExpansionStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpansionStreakTest {
    @Test fun oneCardIsNotAStreak() {
        val s = ExpansionStreak()
        s.record("b1", "sv08")
        assertNull(s.current("b1"))
    }

    @Test fun twoInARowStartAStreakAndItKeepsCounting() {
        val s = ExpansionStreak()
        s.record("b1", "sv08"); s.record("b1", "sv08")
        assertEquals(ExpansionStreak.Streak("sv08", 2), s.current("b1"))
        s.record("b1", "sv08")
        assertEquals(3, s.current("b1")?.count)
    }

    @Test fun aDifferentExpansionEndsTheStreak() {
        val s = ExpansionStreak()
        s.record("b1", "sv08"); s.record("b1", "sv08"); s.record("b1", "sv04.5")
        assertNull(s.current("b1"))
        s.record("b1", "sv04.5")
        assertEquals("sv04.5", s.current("b1")?.setId)
    }

    @Test fun eachBinderHasItsOwnStreak() {
        val s = ExpansionStreak()
        s.record("b1", "sv08"); s.record("b2", "sv08")
        assertNull(s.current("b1"))
        assertNull(s.current("b2"))
    }

    @Test fun preferSetMovesMatchingGuessesFirstAndKeepsOrder() {
        val guesses = listOf("a" to "x", "b" to "sv08", "c" to "y", "d" to "sv08")
        assertEquals(listOf("b", "d", "a", "c"), ExpansionStreak.preferSet(guesses, "sv08") { it.second }.map { it.first })
        assertEquals(guesses, ExpansionStreak.preferSet(guesses, null) { it.second })
    }
}
