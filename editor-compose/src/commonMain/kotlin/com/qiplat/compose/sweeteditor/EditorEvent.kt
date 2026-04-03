package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import kotlin.reflect.KClass

sealed interface EditorEvent

data class TextChangedEvent(
    val editResult: TextEditResult,
) : EditorEvent

data class CursorChangedEvent(
    val position: TextPosition,
) : EditorEvent

data class SelectionChangedEvent(
    val selection: TextRange?,
) : EditorEvent

data class ScrollChangedEvent(
    val scrollMetrics: ScrollMetrics,
) : EditorEvent

data class ScaleChangedEvent(
    val scale: Float,
) : EditorEvent

data class DocumentLoadedEvent(
    val document: com.qiplat.compose.sweeteditor.runtime.EditorDocument?,
) : EditorEvent

data class FoldToggleEvent(
    val line: Int,
) : EditorEvent

data class GutterIconClickEvent(
    val hitTarget: HitTarget,
) : EditorEvent

data class InlayHintClickEvent(
    val hitTarget: HitTarget,
) : EditorEvent

data class LongPressEvent(
    val point: GesturePoint,
) : EditorEvent

data class DoubleTapEvent(
    val point: GesturePoint,
) : EditorEvent

data class ContextMenuEvent(
    val request: EditorContextMenuRequest,
) : EditorEvent

fun interface EditorEventSubscription {
    fun dispose()
}

class EditorEventBus {
    private val listeners = mutableMapOf<KClass<out EditorEvent>, MutableMap<Long, (EditorEvent) -> Unit>>()
    private var nextId: Long = 1L

    @Suppress("UNCHECKED_CAST")
    fun <T : EditorEvent> subscribe(
        eventType: KClass<T>,
        listener: (T) -> Unit,
    ): EditorEventSubscription {
        val listenerId = nextId++
        val bucket = listeners.getOrPut(eventType) { linkedMapOf() }
        bucket[listenerId] = { event -> listener(event as T) }
        return EditorEventSubscription {
            val currentBucket = listeners[eventType] ?: return@EditorEventSubscription
            currentBucket.remove(listenerId)
            if (currentBucket.isEmpty()) {
                listeners.remove(eventType)
            }
        }
    }

    fun publish(event: EditorEvent) {
        listeners[event::class]?.values?.toList()?.forEach { listener ->
            listener(event)
        }
    }

    fun clear() {
        listeners.clear()
    }
}

inline fun <reified T : EditorEvent> EditorEventBus.subscribe(
    noinline listener: (T) -> Unit,
): EditorEventSubscription = subscribe(T::class, listener)
