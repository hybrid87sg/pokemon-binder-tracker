package com.hybrid87sg.bindertracker.ui.components

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlin.math.max

/**
 * True for shiny cards (holo, ultra rare, special art and so on), which
 * get the rainbow shine that follows the phone's tilt.
 */
fun isShinyRarity(rarity: String?): Boolean {
    val r = rarity?.lowercase() ?: return false
    return ShinyWords.any { it in r }
}

private val ShinyWords = listOf(
    "holo", "ultra", "secret", "illustration", "hyper", "double rare", "ace", "shiny", "amazing",
    "radiant", "rainbow", "full art", "legend", "crown", "prism", "star", "gold", "mega",
)

/**
 * How the phone is tilted, -1 to 1 on each axis (x: left/right, y:
 * forwards/back), compared with how it's usually held, smoothed. It only
 * listens to the phone's motion sensor while the screen is showing. When
 * "remove animations" is on in the phone's settings, it stays still.
 */
@Composable
fun rememberPhoneTilt(enabled: Boolean): State<Offset> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilt = remember { mutableStateOf(RestingTilt) }
    val animationsOff = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    DisposableEffect(enabled, animationsOff, lifecycleOwner) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var base: Offset? = null
        var smooth = Offset.Zero
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val now = Offset(event.values[0] / SensorManager.GRAVITY_EARTH, event.values[1] / SensorManager.GRAVITY_EARTH)
                val b = base ?: now
                // "Usually held" drifts slowly to how the phone is held now.
                base = b + (now - b) * 0.01f
                smooth += (now - b - smooth) * 0.2f
                tilt.value = Offset((-smooth.x * 2.5f).coerceIn(-1f, 1f), (smooth.y * 2.5f).coerceIn(-1f, 1f))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val active = enabled && !animationsOff
        var listening = false
        fun listen(on: Boolean) {
            if (!active || sensors == null || sensor == null || on == listening) return
            listening = on
            if (on) sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI) else sensors.unregisterListener(listener)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> listen(true)
                Lifecycle.Event.ON_PAUSE -> listen(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) listen(true)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            listen(false)
        }
    }
    return tilt
}

// A gentle fixed tilt, so the shine still shows when the sensor is off.
private val RestingTilt = Offset(0.35f, -0.25f)

/**
 * A shiny card: it tilts with the phone (up to 15 degrees), with a rainbow
 * shine and a white glare that move with the tilt.
 */
fun Modifier.holoTilt(tilt: State<Offset>): Modifier = this
    .graphicsLayer {
        val t = tilt.value
        rotationY = t.x * 15f
        rotationX = -t.y * 15f
        cameraDistance = 8f * density
        scaleX = 1.02f
        scaleY = 1.02f
    }
    .drawWithContent {
        drawContent()
        val t = tilt.value
        val w = size.width
        val h = size.height
        // The rainbow band slides across as the card tilts.
        val shiftX = (t.x * 0.5f + 0.5f) * w * 1.5f
        val shiftY = (t.y * 0.5f + 0.5f) * h * 0.6f
        val start = Offset(-w * 1.5f + shiftX, -h * 0.6f + shiftY)
        val holo = AppColors.Holo
        val stops = buildList {
            add(0f to Color.Transparent)
            add(0.2f to Color.Transparent)
            holo.forEachIndexed { i, c -> add((0.35f + 0.3f * i / (holo.size - 1).coerceAtLeast(1)) to c) }
            add(0.8f to Color.Transparent)
            add(1f to Color.Transparent)
        }.toTypedArray()
        drawRect(Brush.linearGradient(*stops, start = start, end = start + Offset(w * 2.5f, h * 1.2f)), alpha = 0.6f, blendMode = BlendMode.Overlay)
        // The glare: a soft white spot where the light catches it.
        val spot = Offset(w * (0.5f + t.x * 0.5f), h * (0.5f + t.y * 0.5f))
        drawRect(
            Brush.radialGradient(0f to Color.White.copy(alpha = 0.55f), 0.45f to Color.Transparent, center = spot, radius = max(w, h)),
            blendMode = BlendMode.Softlight,
        )
    }

/**
 * A clear plastic pocket sleeve round a card: a tinted inside, a thin edge,
 * the opening along the top, and a diagonal shine over the card.
 */
fun Modifier.pocketSleeve(): Modifier = this
    .drawBehind {
        val r = CornerRadius(7.dp.toPx())
        drawRoundRect(AppColors.PocketFill, cornerRadius = r)
        drawRoundRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.08f), Color.Transparent), endY = 4.dp.toPx()), cornerRadius = r)
    }
    .drawWithContent {
        drawContent()
        val w = size.width
        val h = size.height
        val r = CornerRadius(7.dp.toPx())
        val edge = AppColors.SleeveEdge
        // The diagonal shine on the plastic.
        drawRect(
            Brush.linearGradient(
                0f to Color.Transparent,
                0.22f to Color.Transparent,
                0.30f to AppColors.SleeveShine,
                0.38f to Color.Transparent,
                0.62f to Color.Transparent,
                0.66f to Color.White.copy(alpha = 0.18f),
                0.70f to Color.Transparent,
                1f to Color.Transparent,
                start = Offset(0f, 0f),
                end = Offset(w, w * 0.53f),
            ),
        )
        drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent), endY = h * 0.4f))
        // The sleeve's edge and its opening along the top.
        drawRoundRect(edge, cornerRadius = r, style = Stroke(1.dp.toPx()))
        val inset = 1.dp.toPx()
        drawRoundRect(
            Color.White.copy(alpha = 0.25f),
            topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            cornerRadius = r,
            style = Stroke(1.dp.toPx()),
        )
        drawRect(edge, topLeft = Offset(r.x * 0.5f, 0f), size = Size(w - r.x, 2.dp.toPx()))
    }
