package me.ray.nexahub.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableListItemScope

private const val DraggingScale = 0.95f

/**
 * Shared drag-to-reorder gesture for lists: long-press the whole item to start dragging, with haptic feedback and a scale effect while dragging.
 *
 * Use it inside `ReorderableItem { isDragging -> }` as the item's modifier:
 * ```
 * ReorderableItem(state, key) { isDragging ->
 *     MyItem(modifier = longPressReorder(isDragging))
 * }
 * ```
 */
@Composable
fun ReorderableCollectionItemScope.longPressReorder(
    isDragging: Boolean,
    enabled: Boolean = true,
): Modifier {
    val haptic = LocalHapticFeedback.current
    return Modifier
        .draggingScale(isDragging)
        .longPressDraggableHandle(
            enabled = enabled,
            onDragStarted = { haptic.dragStarted() },
            onDragStopped = { haptic.dragStopped() },
        )
}

/**
 * The `ReorderableColumn` / `ReorderableRow` version of [longPressReorder].
 */
@Composable
fun ReorderableListItemScope.longPressReorder(
    isDragging: Boolean,
    enabled: Boolean = true,
): Modifier {
    val haptic = LocalHapticFeedback.current
    return Modifier
        .draggingScale(isDragging)
        .longPressDraggableHandle(
            enabled = enabled,
            onDragStarted = { haptic.dragStarted() },
            onDragStopped = { haptic.dragStopped() },
        )
}

@Composable
private fun Modifier.draggingScale(isDragging: Boolean): Modifier {
    val scale by animateFloatAsState(if (isDragging) DraggingScale else 1f, label = "reorder_scale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

private fun HapticFeedback.dragStarted() = performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

private fun HapticFeedback.dragStopped() = performHapticFeedback(HapticFeedbackType.GestureEnd)
