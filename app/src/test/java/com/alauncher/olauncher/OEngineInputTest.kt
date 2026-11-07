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


    @Test fun androidKeysMapToGlfwKeyNamespace() {
        assertEquals(65, OEngineGlfwKeyMapper.key(29)) // Android A
        assertEquals(87, OEngineGlfwKeyMapper.key(51)) // Android W
        assertEquals(48, OEngineGlfwKeyMapper.key(7))  // Android 0
        assertEquals(290, OEngineGlfwKeyMapper.key(131)) // Android F1
        assertEquals(257, OEngineGlfwKeyMapper.key(66)) // Android Enter
        assertNull(OEngineGlfwKeyMapper.key(9999))
    }

    @Test fun androidModifiersMapToGlfwModifierBits() {
        val meta = 0x1 or 0x1000 or 0x2 or 0x10000 or 0x100000
        assertEquals(0x1 or 0x2 or 0x4 or 0x8 or 0x10,
            OEngineGlfwKeyMapper.modifiers(meta))
    }

    @Test fun androidMouseButtonsMapToGlfwIndices() {
        assertEquals(0, OEngineGlfwKeyMapper.mouseButton(1))
        assertEquals(1, OEngineGlfwKeyMapper.mouseButton(2))
        assertEquals(2, OEngineGlfwKeyMapper.mouseButton(4))
        assertEquals(3, OEngineGlfwKeyMapper.mouseButton(8))
        assertEquals(4, OEngineGlfwKeyMapper.mouseButton(16))
        assertNull(OEngineGlfwKeyMapper.mouseButton(0))
    }

    @Test fun motionCoalescingPreservesOrderAcrossKeyTransitions() {
        val queue = OEngineInputQueue(capacity = 8)
        queue.offer(OEngineInputEvent(OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0, x = 1f))
        queue.offer(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, 87))
        queue.offer(OEngineInputEvent(OEngineInputDevice.MOUSE, OEngineInputAction.MOVE, 0, x = 9f))
        val drained = queue.drain()
        assertEquals(listOf(OEngineInputAction.MOVE, OEngineInputAction.DOWN), drained.map { it.action })
        assertEquals(9f, drained[0].x, 0f)
    }


    @Test fun focusLossSynthesizesUpsInReversePressOrder() {
        val tracker = OEngineInputStateTracker()
        assertNotNull(tracker.accept(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, 87)))
        assertNotNull(tracker.accept(OEngineInputEvent(OEngineInputDevice.MOUSE, OEngineInputAction.DOWN, 0)))
        assertEquals(2, tracker.pressedCount)
        val releases = tracker.releaseAll(123L)
        assertEquals(listOf(OEngineInputDevice.MOUSE, OEngineInputDevice.KEYBOARD),
            releases.map { it.device })
        assertTrue(releases.all { it.action == OEngineInputAction.UP && it.timestampNanos == 123L })
        assertEquals(0, tracker.pressedCount)
    }

    @Test fun duplicateDownAndUnmatchedUpDoNotCorruptPressedState() {
        val tracker = OEngineInputStateTracker()
        val down = OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, 65)
        assertNotNull(tracker.accept(down))
        assertNull(tracker.accept(down))
        assertNull(tracker.accept(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.UP, 66)))
        assertEquals(1, tracker.pressedCount)
        assertNotNull(tracker.accept(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.UP, 65)))
        assertEquals(0, tracker.pressedCount)
    }

    @Test fun releaseDeviceLeavesOtherDeviceControlsHeld() {
        val tracker = OEngineInputStateTracker()
        tracker.accept(OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, 87))
        tracker.accept(OEngineInputEvent(OEngineInputDevice.GAMEPAD, OEngineInputAction.DOWN, 0))
        val releases = tracker.releaseDevice(OEngineInputDevice.GAMEPAD, 9L)
        assertEquals(listOf(0), releases.map { it.code })
        assertEquals(1, tracker.pressedCount)
        assertEquals(listOf(87), tracker.releaseAll().map { it.code })
    }


    @Test fun gameThreadBridgeDeliversOrderedDigitalTransitions() {
        OEngineGameInputBridge.resetForTests()
        try {
            val down = OEngineInputEvent(OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, 87, value = 1f)
            val up = down.copy(action = OEngineInputAction.UP, value = 0f)
            assertTrue(OEngineGameInputBridge.enqueue(down))
            assertEquals(listOf(down), OEngineGameInputBridge.drainForGameThread().toList())
            assertTrue(OEngineGameInputBridge.enqueue(up))
            assertEquals(listOf(up), OEngineGameInputBridge.drainForGameThread().toList())
        } finally {
            OEngineGameInputBridge.resetForTests()
        }
    }

    @Test fun gameThreadBridgeReleasesAllHeldControlsAfterTransitionOverflow() {
        OEngineGameInputBridge.resetForTests()
        try {
            repeat(OEngineInputQueue.DEFAULT_CAPACITY) { code ->
                assertTrue(OEngineGameInputBridge.enqueue(OEngineInputEvent(
                    OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, code, value = 1f
                )))
            }
            assertFalse(OEngineGameInputBridge.enqueue(OEngineInputEvent(
                OEngineInputDevice.KEYBOARD, OEngineInputAction.DOWN, 900, value = 1f
            )))
            assertTrue(OEngineGameInputBridge.inputResetRequired)
            val releases = OEngineGameInputBridge.drainForGameThread()
            assertEquals(OEngineInputQueue.DEFAULT_CAPACITY + 1, releases.size)
            assertTrue(releases.all { it.action == OEngineInputAction.UP })
            assertFalse(OEngineGameInputBridge.inputResetRequired)
        } finally {
            OEngineGameInputBridge.resetForTests()
        }
    }
}
