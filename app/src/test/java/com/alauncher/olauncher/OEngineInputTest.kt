package com.alauncher.olauncher

import org.junit.Assert.*
import org.junit.Test

class OEngineInputTest {
    @Test fun resolvesKeyboardMouseAndGamepadBindings() {
        val bindings = OEngineInputBindings(listOf(
            OEngineControlBinding("move.forward", OEngineInputDevice.KEYBOARD, 87, "W"),
            OEngineControlBinding("camera.primary", OEngineInputDevice.MOUSE, 1, "Left mouse"),
            OEngineControlBinding("jump", OEngineInputDevice.GAMEPAD, 0, "A")
        ))
        assertEquals("move.forward", bindings.resolve(OEngineInputDevice.KEYBOARD, 87)?.controlId)
        assertEquals("camera.primary", bindings.resolve(OEngineInputDevice.MOUSE, 1)?.controlId)
        assertEquals("jump", bindings.resolve(OEngineInputDevice.GAMEPAD, 0)?.controlId)
        assertNull(bindings.resolve(OEngineInputDevice.TOUCH, 87))
    }

    @Test fun rejectsDuplicatePhysicalBindings() {
        try {
            OEngineInputBindings(listOf(
                OEngineControlBinding("jump", OEngineInputDevice.KEYBOARD, 32, "Space"),
                OEngineControlBinding("sneak", OEngineInputDevice.KEYBOARD, 32, "Space")
            ))
            fail("Duplicate physical input should be rejected.")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun rejectsInvalidEventPayloads() {
        try {
            OEngineInputEvent(OEngineInputDevice.MOUSE, OEngineInputAction.TEXT, 0, text = "x")
            fail("Mouse text input should be rejected.")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun boundedQueueEvictsMotionBeforeButtonTransitions() {
        val queue = OEngineInputQueue(capacity = 8)
        repeat(8) { index ->
            queue.offer(OEngineInputEvent(
                OEngineInputDevice.MOUSE,
                if (index == 0) OEngineInputAction.MOVE else OEngineInputAction.DOWN,
                index
            ))
        }
        val accepted = queue.offer(OEngineInputEvent(
            OEngineInputDevice.KEYBOARD, OEngineInputAction.UP, 32
        ))
        assertTrue(accepted)
        assertEquals(8, queue.size)
        assertEquals(1L, queue.droppedEventCount)
        assertTrue(queue.drain().any {
            it.device == OEngineInputDevice.KEYBOARD && it.action == OEngineInputAction.UP
        })
    }

    @Test fun queueRejectsMotionWhenOnlyTransitionsRemain() {
        val queue = OEngineInputQueue(capacity = 8)
        repeat(8) { index ->
            queue.offer(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, index))
        }
        assertFalse(queue.offer(OEngineInputEvent(
            OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0
        )))
        assertEquals(8, queue.size)
        assertEquals(1L, queue.droppedEventCount)
    }

    @Test fun queueDrainRespectsLimitAndFifoOrder() {
        val queue = OEngineInputQueue(capacity = 8)
        repeat(3) { index ->
            queue.offer(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, index))
        }
        assertEquals(listOf(0, 1), queue.drain(2).map { it.code })
        assertEquals(1, queue.size)
        assertEquals(2, queue.poll()?.code)
    }


    @Test fun transitionOverflowRequestsAllControlsUpResetWithoutEvictingQueuedTransitions() {
        val queue = OEngineInputQueue(capacity = 8)
        repeat(8) { index ->
            queue.offer(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, index))
        }
        assertFalse(queue.offer(OEngineInputEvent(
            OEngineInputDevice.KEYBOARD, OEngineInputAction.UP, 0
        )))
        assertTrue(queue.inputResetRequired)
        assertTrue(queue.consumeInputResetRequired())
        assertFalse(queue.inputResetRequired)
        assertEquals((0..7).toList(), queue.drain().map { it.code })
    }

    @Test fun repeatedMotionForSameControlIsCoalescedToNewestSample() {
        val queue = OEngineInputQueue(capacity = 8)
        queue.offer(OEngineInputEvent(OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0, x = 1f))
        queue.offer(OEngineInputEvent(OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0, x = 9f))
        assertEquals(1, queue.size)
        assertEquals(9f, queue.poll()?.x ?: 0f, 0f)
        assertEquals(1L, queue.droppedEventCount)
    }

}
