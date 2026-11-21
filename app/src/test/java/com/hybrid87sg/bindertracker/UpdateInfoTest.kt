package com.hybrid87sg.bindertracker

import com.hybrid87sg.bindertracker.update.UpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks the app reads latest.json exactly as GitHub Actions writes it. */
@RunWith(RobolectricTestRunner::class)
class UpdateInfoTest {
    @Test
    fun readsLatestJson() {
        // Same text the "Publish pre-release" step in build.yml produces.
        val text = """{"versionCode":7,"versionName":"0.1.7","apkUrl":"https://github.com/hybrid87sg/pokemon-binder-tracker/releases/download/debug-latest/binder-tracker-v0.1.7.apk"}"""
        val info = UpdateInfo.fromJson(text)
        assertEquals(7, info.versionCode)
        assertEquals("0.1.7", info.versionName)
        assertEquals(
            "https://github.com/hybrid87sg/pokemon-binder-tracker/releases/download/debug-latest/binder-tracker-v0.1.7.apk",
            info.apkUrl,
        )
    }
}
