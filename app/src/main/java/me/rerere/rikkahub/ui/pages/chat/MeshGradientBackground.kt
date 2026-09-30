package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gemini-style animated gradient background.
 *
 * How it works:
 *  1. A linear gradient at the bottom layer (bluish at the top, whitish at the bottom).
 *  2. A few radialGradient light spots on top (colored center → transparent edges), which are naturally soft.
 *  3. Each spot uses an infinite animation with its own period, drifting slowly along a sine/cosine path.
 *
 * Doesn't rely on Modifier.blur, so it works on all API levels and performs better.
 */
@Composable
fun MeshGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val transition = rememberInfiniteTransition(label = "aurora")

    // One phase (0..2π) per spot, with different durations → staggered drift, avoiding uniform motion
    // The loops parameter scales up the target value and total animation duration as needed
    @Composable
    fun phase(durationMillis: Int, loops: Int, label: String) = transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI * loops).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis * loops, easing = LinearEasing)),
        label = label,
    )
    // For each spot's own multiplier, pass the least common multiple of loops needed to eliminate the fraction
    val p1 by phase(5_500, loops = 20, "p1") // 1.15 needs 20 loops (110s total)
    val p2 by phase(7_000, loops = 1,  "p2") // 1.0 needs 1 loop
    val p3 by phase(8_500, loops = 10, "p3") // 0.9 needs 10 loops (85s total)
    val p4 by phase(6_200, loops = 10, "p4") // 1.1 needs 10 loops (62s total)

    val dark = LocalDarkMode.current
    val baseGradient = if (dark) {
        // Dark: deep blue at the top, fading downward to near black
        arrayOf(
            0.0f to Color(0xFF1B2A45),
            0.22f to Color(0xFF15223A),
            0.45f to Color(0xFF0D1626),
            0.65f to Color(0xFF0A0F18),
            1.0f to Color(0xFF080B12),
        )
    } else {
        // Light: bluish at the top, fading downward to white
        arrayOf(
            0.0f to Color(0xFFAFD0F2),
            0.22f to Color(0xFFCBE0F6),
            0.45f to Color(0xFFF1F7FD),
            0.65f to Color(0xFFFFFFFF),
            1.0f to Color(0xFFFFFFFF),
        )
    }

    // Spot colors (blue / cyan / light blue / warm) and intensity, one set each for light and dark
    val blobBlue = if (dark) Color(0xFF3E6FB0) else Color(0xFF9EC5F0)
    val blobTeal = if (dark) Color(0xFF2E7D74) else Color(0xFFA8E6E0)
    val blobLightBlue = if (dark) Color(0xFF4A6E96) else Color(0xFFB6D7F2)
    val blobWarm = if (dark) Color(0xFF7C5F9E) else Color(0xFFFFC8D2)
    val alphaBlue = if (dark) 0.56f else 0.72f
    val alphaTeal = if (dark) 0.44f else 0.56f
    val alphaLightBlue = if (dark) 0.48f else 0.62f
    val alphaWarm = if (dark) 0.32f else 0.42f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colorStops = baseGradient)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Spot radius is a fraction of the screen's longer side; it has to be large enough to look soft
            val r = maxOf(w, h)

            // All spots gather at the top and fade downward, leaving the lower half of the screen blank
            // Blue at the top (primary color, drifting slowly sideways)
            drawBlob(
                center = Offset(
                    w * 0.48f + sin(p1) * w * 0.38f,
                    h * 0.08f + cos(p1 * 1.15f) * h * 0.18f,
                ),
                radius = r * 0.36f,
                color = blobBlue,
                centerAlpha = alphaBlue,
            )
            // Teal accent at the top left
            drawBlob(
                center = Offset(
                    w * 0.18f + sin(p2 + PI.toFloat() * 0.55f) * w * 0.30f,
                    h * 0.24f + cos(p2) * h * 0.20f,
                ),
                radius = r * 0.28f,
                color = blobTeal,
                centerAlpha = alphaTeal,
            )
            // Light blue at the top right
            drawBlob(
                center = Offset(
                    w * 0.82f + sin(p3 + PI.toFloat() * 0.9f) * w * -0.34f,
                    h * 0.12f + cos(p3 * 0.9f) * h * 0.18f,
                ),
                radius = r * 0.30f,
                color = blobLightBlue,
                centerAlpha = alphaLightBlue,
            )
            // A warm spot gives the motion a more visible color reference
            drawBlob(
                center = Offset(
                    w * 0.58f + sin(p4 + PI.toFloat() * 1.25f) * w * 0.28f,
                    h * 0.34f + cos(p4 * 1.1f) * h * 0.16f,
                ),
                radius = r * 0.26f,
                color = blobWarm,
                centerAlpha = alphaWarm,
            )
        }

        content()
    }
}

/** Draw a soft light spot: colored center fading outward to transparent. */
private fun DrawScope.drawBlob(
    center: Offset,
    radius: Float,
    color: Color,
    centerAlpha: Float = 0.75f,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = centerAlpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
