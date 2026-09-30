package me.rerere.rikkahub.ui.modifier

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

/**
 * Modifier that adds a shimmer loading effect to a Composable.
 *
 * @param isLoading whether to show the shimmer effect.
 * @param shimmerColor the bright part of the shimmer.
 * @param backgroundColor the background color of the shimmer gradient (usually translucent, to blend with the original content).
 * @param durationMillis how long one sweep of the animation takes (milliseconds).
 * @param angle the angle of the shimmer (degrees). 0 is left to right, 90 is top to bottom.
 * @param gradientWidthRatio the width of the shimmer gradient relative to the component size. For example, 0.5f makes the shimmer half the component's width.
 */
@Composable
fun Modifier.shimmer(
    isLoading: Boolean,
    shimmerColor: Color = LocalContentColor.current.copy(alpha = 0.3f), // Brighter shimmer color
    backgroundColor: Color = LocalContentColor.current.copy(alpha = 0.9f), // Darker background/base color
    durationMillis: Int = 1200,
    angle: Float = 20f, // Slightly tilted angle
    gradientWidthRatio: Float = 0.5f // Shimmer is half the component's width
): Modifier = composed { // Use composed so remember, LaunchedEffect, etc. can be used inside the Modifier
    if (!isLoading) {
        // When not loading, apply no effect
        this
    } else {
        // Remember the component size to compute the gradient
        var size by remember { mutableStateOf(IntSize.Zero) }
        // Create an infinitely repeating animation
        val transition = rememberInfiniteTransition(label = "ShimmerTransition")
        val translateAnimation = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f, // Animate from 0 to 1
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart // Restart from the beginning each time
            ),
            label = "ShimmerTranslate"
        )
        // Convert the angle to radians
        val angleRad = Math.toRadians(angle.toDouble()).toFloat()
        // Compute the list of gradient colors
        val colors = remember(shimmerColor, backgroundColor) {
            listOf(
                backgroundColor, // Starting background color
                shimmerColor,    // Bright middle color
                backgroundColor  // Ending background color
            )
        }
        // Apply the drawing effect
        this
            .onGloballyPositioned { layoutCoordinates ->
                // Get the component's actual size
                size = layoutCoordinates.size
            }
            .graphicsLayer { alpha = 0.99f } // Enable blending
            .drawWithContent { // Use drawWithContent to get the drawing context
                if (size == IntSize.Zero) {
                    // If the size is unknown, draw the original content first
                    drawContent()
                    return@drawWithContent
                }
                val width = size.width.toFloat()
                val height = size.height.toFloat()
                // Compute the actual gradient width (pixels)
                // The diagonal length has to be considered so it fully covers the component when tilted
                val diagonal = kotlin.math.sqrt(width * width + height * height)
                val gradientWidth = diagonal * gradientWidthRatio
                // Compute the offset of the animation's current position
                // The animation value goes from 0 to 1 and maps to the distance moved
                // The total distance must cover the component plus the gradient width so it sweeps all the way across
                // It starts fully to the left/top of the component and ends fully to the right/bottom
                val totalDistance = diagonal + gradientWidth
                val currentOffset = translateAnimation.value * totalDistance - gradientWidth
                // Compute the gradient's start and end points, taking the angle into account
                val startX = currentOffset * kotlin.math.cos(angleRad)
                val startY = currentOffset * kotlin.math.sin(angleRad)
                val endX = (currentOffset + gradientWidth) * kotlin.math.cos(angleRad)
                val endY = (currentOffset + gradientWidth) * kotlin.math.sin(angleRad)
                // Create the linear gradient Brush
                val shimmerBrush = Brush.linearGradient(
                    colors = colors,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    tileMode = TileMode.Clamp // Clamp mode keeps the gradient colors fixed at the edges
                )
                // 1. Draw the original content first
                drawContent()
                // 2. Draw a rectangle over the original content using the shimmer Brush and the DstIn blend mode
                // BlendMode.DstIn: keeps only the part of the destination (original content) that overlaps the source (shimmer gradient),
                // using the source's alpha. The bright parts of the gradient show the content, and the dark (transparent) parts hide it.
                drawRect(
                    brush = shimmerBrush,
                    blendMode = BlendMode.DstIn
                )
            }
    }
}
