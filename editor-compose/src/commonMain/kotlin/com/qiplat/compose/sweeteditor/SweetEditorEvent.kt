package com.qiplat.compose.sweeteditor

import com.qiplat.compose.sweeteditor.model.foundation.*
import com.qiplat.compose.sweeteditor.model.visual.ScrollMetrics
import kotlin.reflect.KClass

sealed interface SweetEditorEvent

data class TextChangedEvent(
    val editResult: TextEditResult,
) : SweetEditorEvent

data class CursorChangedEvent(
    val position: TextPosition,
) : SweetEditorEvent

data class SelectionChangedEvent(
    val selection: TextRange?,
) : SweetEditorEvent

data class ScrollChangedEvent(
    val scrollMetrics: ScrollMetrics,
) : SweetEditorEvent

data class ScaleChangedEvent(
    val scale: Float,
) : SweetEditorEvent

data class DocumentLoadedEvent(
    val document: com.qiplat.compose.sweeteditor.runtime.EditorDocument?,
) : SweetEditorEvent

data class FoldToggleEvent(
    val line: Int,
) : SweetEditorEvent

data class GutterIconClickEvent(
    val hitTarget: HitTarget,
) : SweetEditorEvent

data class InlayHintClickEvent(
    val hitTarget: HitTarget,
) : SweetEditorEvent

data class LongPressEvent(
    val point: GesturePoint,
) : SweetEditorEvent

data class DoubleTapEvent(
    val point: GesturePoint,
) : SweetEditorEvent

data class ContextMenuEvent(
    val request: EditorContextMenuRequest,
) : SweetEditorEvent

fun interface SweetEditorEventSubscription {
    fun dispose()
}

class SweetEditorEventBus {
    private val listeners = mutableMapOf<KClass<out SweetEditorEvent>, MutableMap<Long, (SweetEditorEvent) -> Unit>>()
    private var nextId: Long = 1L

    @Suppress("UNCHECKED_CAST")
    fun <T : SweetEditorEvent> subscribe(
        eventType: KClass<T>,
        listener: (T) -> Unit,
    ): SweetEditorEventSubscription {
        val listenerId = nextId++
        val bucket = listeners.getOrPut(eventType) { linkedMapOf() }
        bucket[listenerId] = { event -> listener(event as T) }
        return SweetEditorEventSubscription {
            val currentBucket = listeners[eventType] ?: return@SweetEditorEventSubscription
            currentBucket.remove(listenerId)
            if (currentBucket.isEmpty()) {
                listeners.remove(eventType)
            }
        }
    }

    fun publish(event: SweetEditorEvent) {
        listeners[event::class]?.values?.toList()?.forEach { listener ->
            listener(event)
        }
    }

    fun clear() {
        listeners.clear()
    }
}

inline fun <reified T : SweetEditorEvent> SweetEditorEventBus.subscribe(
    noinline listener: (T) -> Unit,
): SweetEditorEventSubscription = subscribe(T::class, listener)
