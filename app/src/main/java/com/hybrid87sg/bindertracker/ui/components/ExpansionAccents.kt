package com.hybrid87sg.bindertracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hybrid87sg.bindertracker.data.PocketWithCard
import com.hybrid87sg.bindertracker.ui.theme.AppColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The little shapes in an expansion's pattern. All simple shapes of our own, never official logos or symbols. */
enum class Motif { SunMoon, Sparkles, Diamonds, Rings, Waves, Leaves }

/** An expansion era's look on a binder page: a pattern and two colours. */
data class ExpansionAccent(val motif: Motif, val first: Color, val second: Color)

/** The look for a series (TCGdex series ids like "sm" or "sv"), or null for none. */
fun accentFor(seriesId: String?): ExpansionAccent? = when (seriesId) {
    null, "" -> null
    "sm" -> ExpansionAccent(Motif.SunMoon, Color(0xFFE9A23B), Color(0xFF7F8BE8))
    "swsh" -> ExpansionAccent(Motif.Diamonds, Color(0xFF6C9BD2), Color(0xFFD25C6C))
    "sv" -> ExpansionAccent(Motif.Rings, Color(0xFFE0644F), Color(0xFF8E5BD8))
    "xy" -> ExpansionAccent(Motif.Sparkles, Color(0xFF3F7FD8), Color(0xFFD84F6A))
    "bw" -> ExpansionAccent(Motif.Waves, Color(0xFF7A7E92), Color(0xFF5C93F0))
    "base", "gym", "neo", "lc" -> ExpansionAccent(Motif.Sparkles, Color(0xFFE9A23B), Color(0xFF5C93F0))
    else -> ExpansionAccent(Motif.Leaves, Color(0xFF5FAF7A), Color(0xFFE9A23B))
}

/** The expansion most cards on a page come from: its series and set, or null if the page has no cards yet. */
data class PageExpansion(val seriesId: String, val setName: String)

fun pageExpansion(pockets: List<PocketWithCard>): PageExpansion? =
    pockets.filter { it.cardId != null && !it.seriesId.isNullOrEmpty() }
        .groupBy { it.setId }
        .maxByOrNull { it.value.size }
        ?.value?.first()
        ?.let { PageExpansion(it.seriesId.orEmpty(), it.setName.orEmpty()) }

/** A faint tiled pattern of the expansion's shapes, behind the pockets. */
fun Modifier.expansionPattern(accent: ExpansionAccent?): Modifier =
    if (accent == null) this else drawBehind {
        val alpha = if (AppColors.isDark) 0.36f else 0.28f
        val tile = 64.dp.toPx()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                translate(x, y) { drawMotif(accent, alpha) }
                x += tile
            }
            y += tile
        }
    }

private fun DrawScope.drawMotif(accent: ExpansionAccent, alpha: Float) {
    val u = 1.dp.toPx()
    val stroke = Stroke(width = 1.6f * u)
    val a = accent.first.copy(alpha = alpha)
    val b = accent.second.copy(alpha = alpha)
    val one = Offset(16 * u, 16 * u)
    val two = Offset(48 * u, 46 * u)
    when (accent.motif) {
        Motif.SunMoon -> { sun(one, a, stroke, u); moon(two, b, stroke, u) }
        Motif.Sparkles -> { sparkle(one, 7 * u, a, stroke); sparkle(two, 5 * u, b, stroke) }
        Motif.Diamonds -> { diamond(one, 7 * u, a, stroke); drawCircle(b, 4 * u, two, style = stroke) }
        Motif.Rings -> { drawCircle(a, 7 * u, one, style = stroke); drawCircle(a, 2.5f * u, one); drawCircle(b, 5 * u, two, style = stroke) }
        Motif.Waves -> { wave(one, a, stroke, u); drawCircle(b, 4 * u, two, style = stroke) }
        Motif.Leaves -> { leaf(one, a, stroke, u); sparkle(two, 5 * u, b, stroke) }
    }
}

private fun DrawScope.sun(c: Offset, color: Color, stroke: Stroke, u: Float) {
    drawCircle(color, 5 * u, c, style = stroke)
    repeat(8) { k ->
        val ang = k * PI.toFloat() / 4f
        val d = Offset(cos(ang), sin(ang))
        drawLine(color, c + d * (8 * u), c + d * (11 * u), strokeWidth = stroke.width)
    }
}

private fun DrawScope.moon(c: Offset, color: Color, stroke: Stroke, u: Float) {
    // A crescent: the outer edge of one circle and the inner edge of a smaller, shifted one.
    val r = 7 * u
    drawArc(color, startAngle = 60f, sweepAngle = 240f, useCenter = false, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2), style = stroke)
    val r2 = 5.5f * u
    val c2 = c + Offset(-2.2f * u, 0f)
    drawArc(color, startAngle = 80f, sweepAngle = 200f, useCenter = false, topLeft = c2 - Offset(r2, r2), size = Size(r2 * 2, r2 * 2), style = stroke)
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color, stroke: Stroke) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticTo(c.x, c.y, c.x + r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y + r)
        quadraticTo(c.x, c.y, c.x - r, c.y)
        quadraticTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color, style = stroke)
}

private fun DrawScope.diamond(c: Offset, r: Float, color: Color, stroke: Stroke) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r * 0.7f, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r * 0.7f, c.y); close()
    }
    drawPath(p, color, style = stroke)
}

private fun DrawScope.wave(c: Offset, color: Color, stroke: Stroke, u: Float) {
    val p = Path().apply {
        moveTo(c.x - 10 * u, c.y)
        quadraticTo(c.x - 5 * u, c.y - 5 * u, c.x, c.y)
        quadraticTo(c.x + 5 * u, c.y + 5 * u, c.x + 10 * u, c.y)
    }
    drawPath(p, color, style = stroke)
}

private fun DrawScope.leaf(c: Offset, color: Color, stroke: Stroke, u: Float) {
    val p = Path().apply {
        moveTo(c.x - 7 * u, c.y + 5 * u)
        quadraticTo(c.x - 6 * u, c.y - 6 * u, c.x + 7 * u, c.y - 5 * u)
        quadraticTo(c.x + 6 * u, c.y + 6 * u, c.x - 7 * u, c.y + 5 * u)
        close()
    }
    drawPath(p, color, style = stroke)
    drawLine(color, Offset(c.x - 7 * u, c.y + 5 * u), Offset(c.x + 3 * u, c.y - 2 * u), strokeWidth = stroke.width * 0.8f)
}

/** The expansion's name in a small badge tinted with its two colours, e.g. "● Guardians Rising". */
@Composable
fun ExpansionBadge(name: String, accent: ExpansionAccent, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .border(1.dp, accent.first, shape)
            .background(Brush.horizontalGradient(listOf(accent.first.copy(alpha = 0.18f), accent.second.copy(alpha = 0.18f))), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).background(accent.first, CircleShape))
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
