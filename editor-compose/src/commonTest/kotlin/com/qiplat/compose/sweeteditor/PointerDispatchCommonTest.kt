package com.qiplat.compose.sweeteditor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerType
import com.qiplat.compose.sweeteditor.model.foundation.EditorGestureEventType
import com.qiplat.compose.sweeteditor.model.foundation.GesturePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointerDispatchCommonTest {
    @Test
    fun normalizeMouseWheelScrollDeltaScalesDesktopWheelToSwingRange() {
        val normalized = normalizeMouseWheelScrollDelta(
            Offset(0f, -1f),
        )

        assertEquals(Offset(0f, -40f), normalized)
    }

    @Test
    fun normalizeMouseWheelScrollDeltaKeepsZeroStable() {
        val normalized = normalizeMouseWheelScrollDelta(
            Offset.Zero,
        )

        assertEquals(Offset.Zero, normalized)
    }

    @Test
    fun scrollAndMouseDownDispatchInSamePlan() {
        val plan = buildPointerDispatchPlan(
            scrollDelta = Offset(3f, -5f),
            isSecondaryPressed = false,
            changes = listOf(
                PointerChangeSnapshot(
                    type = PointerType.Mouse,
                    position = GesturePoint(10f, 20f),
                    previousPosition = GesturePoint(10f, 20f),
                    pressed = true,
                    changedToDown = true,
                    changedToUp = false,
                ),
            ),
        )

        assertTrue(plan.requestFocus)
        assertEquals(2, plan.dispatches.size)
        assertEquals(EditorGestureEventType.MouseWheel, plan.dispatches[0].type)
        assertEquals(3f, plan.dispatches[0].wheelDeltaX)
        assertEquals(-5f, plan.dispatches[0].wheelDeltaY)
        assertEquals(EditorGestureEventType.MouseDown, plan.dispatches[1].type)
        assertEquals(listOf(GesturePoint(10f, 20f)), plan.dispatches[1].points)
    }

    @Test
    fun touchPinchMoveDispatchesMoveThenDirectScale() {
        val plan = buildPointerDispatchPlan(
            scrollDelta = Offset.Zero,
            isSecondaryPressed = false,
            changes = listOf(
                PointerChangeSnapshot(
                    type = PointerType.Touch,
                    position = GesturePoint(0f, 0f),
                    previousPosition = GesturePoint(1f, 0f),
                    pressed = true,
                    changedToDown = false,
                    changedToUp = false,
                ),
                PointerChangeSnapshot(
                    type = PointerType.Touch,
                    position = GesturePoint(10f, 0f),
                    previousPosition = GesturePoint(9f, 0f),
                    pressed = true,
                    changedToDown = false,
                    changedToUp = false,
                ),
            ),
        )

        assertEquals(2, plan.dispatches.size)
        assertEquals(EditorGestureEventType.TouchMove, plan.dispatches[0].type)
        assertEquals(2, plan.dispatches[0].points.size)
        assertEquals(EditorGestureEventType.DirectScale, plan.dispatches[1].type)
        assertTrue(plan.dispatches[1].directScale > 1f)
    }

    @Test
    fun mouseMoveDispatchesOnlyMovedPoint() {
        val plan = buildPointerDispatchPlan(
            scrollDelta = Offset.Zero,
            isSecondaryPressed = false,
            changes = listOf(
                PointerChangeSnapshot(
                    type = PointerType.Mouse,
                    position = GesturePoint(20f, 24f),
                    previousPosition = GesturePoint(10f, 14f),
                    pressed = true,
                    changedToDown = false,
                    changedToUp = false,
                ),
            ),
        )

        assertEquals(1, plan.dispatches.size)
        assertEquals(EditorGestureEventType.MouseMove, plan.dispatches.first().type)
        assertEquals(listOf(GesturePoint(20f, 24f)), plan.dispatches.first().points)
    }
}
