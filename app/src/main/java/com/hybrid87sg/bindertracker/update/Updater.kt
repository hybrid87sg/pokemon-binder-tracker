package com.hybrid87sg.bindertracker.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hybrid87sg.bindertracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What the latest published version is, read from latest.json on GitHub. */
data class UpdateInfo(
    val versionCode: Int,   // the number Android compares, e.g. 6
    val versionName: String, // the label people see, e.g. "0.1.6"
    val apkUrl: String,      // where to download the new app file
) {
    companion object {
        /** Reads text like {"versionCode":6,"versionName":"0.1.6","apkUrl":"https://..."} */
        fun fromJson(text: String): UpdateInfo {
            val json = JSONObject(text)
            return UpdateInfo(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
            )
        }
    }
}

/**
 * Checks GitHub for a newer version of the app, downloads it and opens
 * Android's installer.
 *
 * Updates install over the old app (keeping your binders) only because every
 * build is signed with the same fixed key in keystore/.
 */
class Updater(private val context: Context) {

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    /** Returns the newer version if there is one, or null if we're up to date. */
    suspend fun findNewerVersion(): UpdateInfo? = withContext(Dispatchers.IO) {
        val text = open(BuildConfig.UPDATE_INFO_URL).inputStream.bufferedReader().use { it.readText() }
        UpdateInfo.fromJson(text).takeIf { it.versionCode > currentVersionCode }
    }

    /**
     * Downloads the new app file into the app's private cache folder.
     * [onProgress] is called with a number from 0.0 to 1.0 as it downloads.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "updates").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() } // remove older downloads
        }
        val file = File(folder, "binder-tracker-v${info.versionName}.apk")
        val connection = open(info.apkUrl)
        val total = connection.contentLengthLong
        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    done += read
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        file
    }

    /** Opens Android's "Do you want to update this app?" screen. */
    fun install(file: File) {
        // A FileProvider lets the installer read a file from our private folder.
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.useCaches = false // always ask GitHub, never use an old copy
        connection.instanceFollowRedirects = true
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("GitHub replied with code $code")
        return connection
    }
}
