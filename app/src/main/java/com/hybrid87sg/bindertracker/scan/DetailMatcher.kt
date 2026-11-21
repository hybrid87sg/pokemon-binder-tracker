package com.hybrid87sg.bindertracker.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import java.net.HttpURLConnection
import java.net.URL

/**
 * The two steps that make recognition precise, using OpenCV (a free image
 * library that runs on the phone):
 *
 * 1. [straighten]: finds the card's four corners in the photo and flattens
 *    it into a straight card shape, so it lines up with official pictures.
 * 2. [pickByDetails]: compares small distinctive details (corners, edges,
 *    shapes in the artwork) between the scan and each candidate's sharp
 *    official picture, and counts how many line up. The real card lines up
 *    far better than look-alikes, even other prints of the same Pokémon.
 *
 * Same method as scripts/recognition_bench.py, which scores it on GitHub.
 */
object DetailMatcher {
    private const val CARD_W = 400
    private const val CARD_H = 557

    private val ready: Boolean by lazy { runCatching { OpenCVLoader.initLocal() }.getOrDefault(false) }

    /**
     * The card cut out and straightened, or null if no card-shaped outline
     * was found (then the photo is used as it is).
     */
    fun straighten(photo: Bitmap): Bitmap? {
        if (!ready) return null
        return runCatching {
            val rgba = Mat().also { Utils.bitmapToMat(photo, it) }
            val gray = Mat().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2GRAY) }
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            val edges = Mat().also { Imgproc.Canny(gray, it, 40.0, 120.0) }
            Imgproc.dilate(edges, edges, Mat.ones(3, 3, CvType.CV_8U))
            val contours = mutableListOf<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            val area = photo.width.toDouble() * photo.height
            for (c in contours.sortedByDescending { Imgproc.contourArea(it) }.take(5)) {
                if (Imgproc.contourArea(c) < area * 0.3) break
                val curve = MatOfPoint2f(*c.toArray())
                val quad = MatOfPoint2f().also { Imgproc.approxPolyDP(curve, it, 0.02 * Imgproc.arcLength(curve, true), true) }
                val pts = quad.toArray()
                if (pts.size != 4) continue
                // Corners in order: top-left, top-right, bottom-right, bottom-left.
                val tl = pts.minBy { it.x + it.y }
                val br = pts.maxBy { it.x + it.y }
                val tr = pts.minBy { it.y - it.x }
                val bl = pts.maxBy { it.y - it.x }
                val from = MatOfPoint2f(tl, tr, br, bl)
                val to = MatOfPoint2f(Point(0.0, 0.0), Point(CARD_W.toDouble(), 0.0), Point(CARD_W.toDouble(), CARD_H.toDouble()), Point(0.0, CARD_H.toDouble()))
                val flat = Mat()
                Imgproc.warpPerspective(rgba, flat, Imgproc.getPerspectiveTransform(from, to), Size(CARD_W.toDouble(), CARD_H.toDouble()))
                return@runCatching Bitmap.createBitmap(CARD_W, CARD_H, Bitmap.Config.ARGB_8888).also { Utils.matToBitmap(flat, it) }
            }
            null
        }.getOrNull()
    }

    /** The small details of a picture: where they are, and what each looks like. */
    private class Details(val points: MatOfKeyPoint, val looks: Mat)

    private fun details(picture: Bitmap): Details? = runCatching {
        val scaled = Bitmap.createScaledBitmap(picture, CARD_W, CARD_H, true)
        val rgba = Mat().also { Utils.bitmapToMat(scaled, it) }
        val gray = Mat().also { Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2GRAY) }
        val points = MatOfKeyPoint()
        val looks = Mat()
        ORB.create(1500).detectAndCompute(gray, Mat(), points, looks)
        if (looks.empty()) null else Details(points, looks)
    }.getOrNull()

    /** How many details of [a] line up with [b] (more = same card). */
    private fun score(a: Details, b: Details): Int = runCatching {
        val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        val pairs = mutableListOf<MatOfDMatch>()
        matcher.knnMatch(a.looks, b.looks, pairs, 2)
        val good = pairs.mapNotNull { p -> p.toArray().takeIf { it.size == 2 && it[0].distance < 0.75f * it[1].distance }?.get(0) }
        if (good.size < 8) return@runCatching good.size
        val ka = a.points.toArray()
        val kb = b.points.toArray()
        val src = MatOfPoint2f(*good.map { ka[it.queryIdx].pt }.toTypedArray())
        val dst = MatOfPoint2f(*good.map { kb[it.trainIdx].pt }.toTypedArray())
        val inliers = Mat()
        Calib3d.findHomography(src, dst, Calib3d.RANSAC, 6.0, inliers)
        if (inliers.empty()) 0 else Core.countNonZero(inliers)
    }.getOrDefault(0)

    // Details of official pictures already downloaded, by card id.
    private val official = LruCache<String, Details>(200)
    private val downloads = Semaphore(6)

    /** One candidate's result: how many details lined up. */
    data class DetailResult(val cardId: String, val matches: Int)

    /**
     * Compares [scan] (already straightened) with the sharp official
     * picture of each candidate (card id to picture address), best first.
     * Needs the internet for pictures not downloaded yet.
     */
    suspend fun pickByDetails(scan: Bitmap, candidates: List<Pair<String, String>>): List<DetailResult> = coroutineScope {
        if (!ready) return@coroutineScope emptyList()
        val mine = withContext(Dispatchers.Default) { details(scan) } ?: return@coroutineScope emptyList()
        candidates.map { (id, imageUrl) ->
            async(Dispatchers.IO) {
                val theirs = official.get(id) ?: downloads.withPermit { download("$imageUrl/high.webp") }?.let(::details)?.also { official.put(id, it) }
                DetailResult(id, theirs?.let { score(mine, it) } ?: 0)
            }
        }.awaitAll().sortedByDescending { it.matches }
    }

    /**
     * Use the top result only if enough details line up and it's clearly
     * ahead of the next (first guesses, to be tuned with the bench).
     */
    fun confident(results: List<DetailResult>): String? {
        val top = results.firstOrNull() ?: return null
        val next = results.getOrNull(1)?.matches ?: 0
        return if (top.matches >= MIN_MATCHES && top.matches >= next * 1.5) top.cardId else null
    }

    const val MIN_MATCHES = 25

    private fun download(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            if (conn.responseCode != 200) null else conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}
