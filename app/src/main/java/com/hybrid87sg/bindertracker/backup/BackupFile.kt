package com.hybrid87sg.bindertracker.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * What goes inside a backup file, and how it is written as text (JSON).
 *
 * A backup is one .zip file holding:
 *  - "backup.json": every binder, its pages and what is in each pocket
 *  - "photos/<page id>.jpg": the photo of each page
 *
 * We write our own simple format instead of copying the database file, so a
 * backup made today still restores after the app's database changes in a
 * later update. [FORMAT] goes up by one if this layout ever has to change.
 */
object BackupFile {
    const val FORMAT = 1
    const val JSON_NAME = "backup.json"
    const val PHOTO_FOLDER = "photos/"
    private const val APP_TAG = "pokemon-binder-tracker"

    data class BinderData(
        val id: String,
        val name: String,
        val colorHex: String,
        val columns: Int,
        val rows: Int,
        val createdAt: Long,
        val pages: List<PageData>,
    )

    data class PageData(
        val id: String,
        // Name of the photo inside the zip, e.g. "photos/abc.jpg", or null if the page had none.
        val photo: String?,
        val guideLeft: Float,
        val guideTop: Float,
        val guideRight: Float,
        val guideBottom: Float,
        val createdAt: Long,
        val pockets: List<PocketData>,
    )

    data class PocketData(val position: Int, val cardId: String?, val isEmpty: Boolean)

    /** The zip entry name for a page's photo. */
    fun photoName(pageId: String) = "$PHOTO_FOLDER$pageId.jpg"

    fun toJson(binders: List<BinderData>, createdAt: Long = System.currentTimeMillis()): String =
        JSONObject()
            .put("app", APP_TAG)
            .put("format", FORMAT)
            .put("createdAt", createdAt)
            .put("binders", JSONArray(binders.map { it.toJson() }))
            .toString(2)

    /**
     * Reads backup.json back. Throws [NotABackup] if the text isn't one of
     * our backups, or was made by a newer app version we don't understand.
     */
    fun fromJson(text: String): List<BinderData> {
        val root = runCatching { JSONObject(text) }.getOrNull()
        if (root == null || root.optString("app") != APP_TAG) throw NotABackup("That file isn't a binder backup.")
        if (root.optInt("format") > FORMAT) throw NotABackup("This backup was made by a newer version of the app. Update the app, then try again.")
        val binders = root.optJSONArray("binders") ?: JSONArray()
        return (0 until binders.length()).map { binderFromJson(binders.getJSONObject(it)) }
    }

    class NotABackup(message: String) : Exception(message)

    private fun BinderData.toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("colorHex", colorHex)
        .put("columns", columns)
        .put("rows", rows)
        .put("createdAt", createdAt)
        .put("pages", JSONArray(pages.map { it.toJson() }))

    private fun PageData.toJson() = JSONObject()
        .put("id", id)
        .put("photo", photo ?: JSONObject.NULL)
        .put("guideLeft", guideLeft.toDouble())
        .put("guideTop", guideTop.toDouble())
        .put("guideRight", guideRight.toDouble())
        .put("guideBottom", guideBottom.toDouble())
        .put("createdAt", createdAt)
        .put("pockets", JSONArray(pockets.map { it.toJson() }))

    private fun PocketData.toJson() = JSONObject()
        .put("position", position)
        .put("cardId", cardId ?: JSONObject.NULL)
        .put("isEmpty", isEmpty)

    private fun binderFromJson(o: JSONObject): BinderData {
        val pages = o.optJSONArray("pages") ?: JSONArray()
        return BinderData(
            id = o.getString("id"),
            name = o.optString("name", "Binder"),
            colorHex = o.optString("colorHex", "#3A3A38"),
            columns = o.optInt("columns", 3).coerceIn(1, 10),
            rows = o.optInt("rows", 3).coerceIn(1, 10),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            pages = (0 until pages.length()).map { pageFromJson(pages.getJSONObject(it)) },
        )
    }

    private fun pageFromJson(o: JSONObject): PageData {
        val pockets = o.optJSONArray("pockets") ?: JSONArray()
        return PageData(
            id = o.getString("id"),
            photo = o.optStringOrNull("photo"),
            guideLeft = o.optDouble("guideLeft", 0.0).toFloat(),
            guideTop = o.optDouble("guideTop", 0.0).toFloat(),
            guideRight = o.optDouble("guideRight", 1.0).toFloat(),
            guideBottom = o.optDouble("guideBottom", 1.0).toFloat(),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            pockets = (0 until pockets.length()).map {
                val p = pockets.getJSONObject(it)
                PocketData(p.getInt("position"), p.optStringOrNull("cardId"), p.optBoolean("isEmpty", false))
            },
        )
    }

    // org.json turns a missing or null value into "" or "null"; we want a real null.
    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
}
