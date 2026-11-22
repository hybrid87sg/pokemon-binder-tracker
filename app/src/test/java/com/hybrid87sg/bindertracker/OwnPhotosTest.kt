package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.data.OwnPhotos
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnPhotosTest {
    @Test fun safeFileNames() {
        assertEquals("sm1-89.jpg", OwnPhotos.fileName("sm1-89"))
        assertEquals("me02.5-017.jpg", OwnPhotos.fileName("me02.5-017"))
        assertEquals("a_b-1.jpg", OwnPhotos.fileName("a/b-1"))
    }
}
