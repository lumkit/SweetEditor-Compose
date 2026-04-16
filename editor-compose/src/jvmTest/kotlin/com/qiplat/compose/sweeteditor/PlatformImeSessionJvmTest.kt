package com.qiplat.compose.sweeteditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformImeSessionJvmTest {
    @Test
    fun shouldSyncJvmImeStateReturnsFalseForUnchangedValue() {
        val value = TextFieldValue(
            text = "hello",
            selection = TextRange(1, 3),
            composition = TextRange(0, 5),
        )

        assertFalse(shouldSyncJvmImeState(value, value.copy()))
    }

    @Test
    fun shouldSyncJvmImeStateReturnsTrueWhenTextChanges() {
        val previous = TextFieldValue(text = "hello")
        val next = TextFieldValue(text = "world")

        assertTrue(shouldSyncJvmImeState(previous, next))
    }

    @Test
    fun shouldTearDownJvmImeSessionReturnsTrueWhenDocumentIsMissing() {
        assertTrue(
            shouldTearDownJvmImeSession(
                textInputServiceAvailable = true,
                documentAvailable = false,
                isFocused = true,
                isReadOnly = false,
            ),
        )
    }

    @Test
    fun computeJvmImeTeardownPlanStopsAndClearsWhenSessionHasState() {
        val plan = computeJvmImeTeardownPlan(
            textInputServiceAvailable = true,
            sessionActive = true,
            isComposing = true,
            currentValue = TextFieldValue(text = "hello"),
        )

        assertEquals(
            JvmImeTeardownPlan(
                stopInput = true,
                cancelComposition = true,
                clearValue = true,
                hideKeyboard = true,
            ),
            plan,
        )
    }

    @Test
    fun computeJvmImeTeardownPlanIsNoOpForEmptyDetachedState() {
        val plan = computeJvmImeTeardownPlan(
            textInputServiceAvailable = false,
            sessionActive = false,
            isComposing = false,
            currentValue = TextFieldValue(),
        )

        assertEquals(
            JvmImeTeardownPlan(
                stopInput = false,
                cancelComposition = false,
                clearValue = false,
                hideKeyboard = false,
            ),
            plan,
        )
    }
}
