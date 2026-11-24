package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.ui.three.BinderModel
import com.hybrid87sg.bindertracker.ui.three.Cam
import com.hybrid87sg.bindertracker.ui.three.ShelfLayout
import com.hybrid87sg.bindertracker.ui.three.Vec3
import com.hybrid87sg.bindertracker.ui.three.hitBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Checks the 3D binder file, the shelf layout and the tapping maths. */
class Binder3DTest {
    private val pics = BinderModel.Pictures(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(4), byteArrayOf(5))

    @Test
    fun binderFileIsAValidGlb() {
        for (dir in listOf(1, -1)) {
            val glb = BinderModel.build(dir, floatArrayOf(0.2f, 0.1f, 0.6f), pics)
            val b = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0x46546C67, b.getInt(0)) // "glTF"
            assertEquals(2, b.getInt(4))
            assertEquals(glb.size, b.getInt(8))
            val jsonLength = b.getInt(12)
            assertEquals(0, jsonLength % 4)
            val json = String(glb, 20, jsonLength)
            // The cover swings on a node called "hinge"; the app looks it up by name.
            assertTrue(json.contains("\"name\":\"hinge\""))
            assertTrue(json.contains("\"name\":\"binder\""))
            // The binary part follows the JSON.
            assertEquals(0x004E4942, b.getInt(20 + jsonLength + 4))
        }
    }

    @Test
    fun shelfPutsFourBindersOnEachRow() {
        val l = ShelfLayout(5, 25, coversOnRight = true)
        assertEquals(5, l.spots.size)
        assertEquals(listOf(0, 0, 0, 0, 1), l.spots.map { it.row })
        assertEquals(2, l.planks.size)
        // Binders on a row go left to right without touching.
        val xs = l.spots.take(4).map { it.position.x }
        assertTrue(xs.zipWithNext().all { (a, b) -> b > a })
        // The whole shelf is centred.
        assertEquals(-l.max.x, l.min.x, 0.001f)
    }

    @Test
    fun turningLeftMirrorsTheBinders() {
        val right = ShelfLayout(4, 30, coversOnRight = true)
        val left = ShelfLayout(4, 30, coversOnRight = false)
        assertEquals(1, right.dir)
        assertEquals(-1, left.dir)
        assertEquals(-right.turn, left.turn, 0.0001f)
        // Straight binders always use the right-hand model.
        assertEquals(1, ShelfLayout(3, 0, coversOnRight = false).dir)
    }

    @Test
    fun aTapFindsTheBinderInFront() {
        val (lo, hi) = BinderModel.bounds(1)
        val eye = Vec3(0.5f, 1f, 10f)
        assertNotNull(hitBox(eye, Vec3(0f, 0f, -1f), lo, hi))
        assertNull(hitBox(eye, Vec3(1f, 0f, 0f), lo, hi))
    }

    @Test
    fun cameraProjectsTheMiddleToTheCentre() {
        val cam = Cam(Vec3(0f, 0f, 10f), Vec3(0f, 0f, 0f), 28f, 0.5f)
        val (x, y) = cam.project(Vec3(0f, 0f, 0f))
        assertEquals(0.5f, x, 0.0001f)
        assertEquals(0.5f, y, 0.0001f)
        val r = cam.ray(0.5f, 0.5f)
        assertEquals(-1f, r.z, 0.0001f)
    }
}
