package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.CardPictures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardPicturesTest {
    @Test fun sunAndMoonSolgaleo() {
        assertEquals("https://images.pokemontcg.io/sm1/89.png", CardPictures.backupUrl("sm1-89", large = false))
        assertEquals("https://images.pokemontcg.io/sm1/89_hires.png", CardPictures.backupUrl("sm1-89", large = true))
    }

    @Test fun promoAndHalfSets() {
        assertEquals("https://images.pokemontcg.io/smp/SM104.png", CardPictures.backupUrl("smp-SM104", large = false))
        assertEquals("https://images.pokemontcg.io/sm35/12.png", CardPictures.backupUrl("sm3.5-12", large = false))
        assertEquals("https://images.pokemontcg.io/swsh12pt5/1.png", CardPictures.backupUrl("swsh12.5-001", large = false))
    }

    @Test fun scarletAndViolet() {
        assertEquals("sv1", CardPictures.backupSetCode("sv01"))
        assertEquals("sv3pt5", CardPictures.backupSetCode("sv03.5"))
        assertEquals("https://images.pokemontcg.io/sv1/25.png", CardPictures.backupUrl("sv01-025", large = false))
    }

    @Test fun nothingToGuessFrom() {
        assertNull(CardPictures.backupUrl(null, large = false))
        assertNull(CardPictures.backupUrl("nodash", large = false))
    }
}
