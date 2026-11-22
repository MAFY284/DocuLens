package com.doculens.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    itemSpacing: Dp = 10.dp,
    itemHeight: Dp = 108.dp,
    key: (T) -> Any,
    content: @Composable (index: Int, item: T, dragging: Boolean, dragHandle: Modifier) -> Unit
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemStepPx = with(density) { (itemHeight + itemSpacing).toPx() }
    val latestSize by rememberUpdatedState(items.size)

    fun dragBy(deltaY: Float) {
        if (draggingIndex < 0) return
        dragOffset += deltaY
        if (dragOffset > itemStepPx * 0.55f) {
            while (dragOffset > itemStepPx * 0.55f && draggingIndex < latestSize - 1) {
                val from = draggingIndex
                onMove(from, from + 1)
                draggingIndex = from + 1
                dragOffset -= itemStepPx
            }
        } else if (dragOffset < -itemStepPx * 0.55f) {
            while (dragOffset < -itemStepPx * 0.55f && draggingIndex > 0) {
                val from = draggingIndex
                onMove(from, from - 1)
                draggingIndex = from - 1
                dragOffset += itemStepPx
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
            val latestIndex by rememberUpdatedState(index)
            val dragging = draggingIndex == index
            val handleModifier = Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastPosition = down.position
                    var started = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (started) {
                                draggingIndex = -1
                                dragOffset = 0f
                            }
                            change.consume()
                            break
                        }
                        val delta = change.position - lastPosition
                        lastPosition = change.position
                        change.consume()
                        if (!started) {
                            val total = change.position - down.position
                            if (total.getDistance() > viewConfiguration.touchSlop) {
                                started = true
                                draggingIndex = latestIndex
                                dragOffset = 0f
                                dragBy(delta.y)
                            }
                        } else {
                            dragBy(delta.y)
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) 1f else 0f)
                    .offsetY(if (dragging) dragOffset else 0f)
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = latestIndex
                                dragOffset = 0f
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragBy(amount.y)
                            }
                        )
                    }
            ) {
                content(index, item, dragging, handleModifier)
            }
        }
    }
}

private fun Modifier.offsetY(offset: Float): Modifier {
    return this.then(
        Modifier.offset {
            IntOffset(0, offset.roundToInt())
        }
    )
}
