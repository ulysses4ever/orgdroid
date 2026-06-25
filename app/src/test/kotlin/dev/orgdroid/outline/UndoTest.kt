package dev.orgdroid.outline

import dev.orgdroid.org.OrgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class UndoTest {

    private fun snap(text: String): UndoSnapshot {
        val root = OrgParser.parse(text)
        return UndoSnapshot(
            root = root,
            collapsed = emptySet(),
            focusedRoot = null,
            dirty = false,
        )
    }

    @Test fun pushAddsToEnd() {
        val a = snap("* A\n")
        val b = snap("* B\n")
        val stack = UndoOps.push(emptyList(), a)
        val next = UndoOps.push(stack, b)
        assertEquals(listOf(a, b), next)
    }

    @Test fun pushDedupesIdenticalTop() {
        // Use the same snapshot instance twice — don't depend on parser ID
        // stability across parses (OrgParser doesn't guarantee it).
        val a = snap("* A\n")
        val stack = UndoOps.push(emptyList(), a)
        val next = UndoOps.push(stack, a)
        assertSame(stack, next)
    }

    @Test fun pushDifferentSnapshotsAreNotDeduped() {
        val a = snap("* A\n")
        val b = snap("* B\n")
        val stack = UndoOps.push(UndoOps.push(emptyList(), a), b)
        assertEquals(2, stack.size)
    }

    @Test fun pushCapsAtLimit() {
        var stack = emptyList<UndoSnapshot>()
        repeat(UndoOps.LIMIT + 5) { i ->
            stack = UndoOps.push(stack, snap("* H$i\n"))
        }
        assertEquals(UndoOps.LIMIT, stack.size)
        assertEquals("H5", stack.first().root.children.single().title)
    }

    @Test fun pushDropsOldestOnOverflow() {
        var stack = emptyList<UndoSnapshot>()
        repeat(UndoOps.LIMIT) { i -> stack = UndoOps.push(stack, snap("* H$i\n")) }
        val newest = snap("* NEW\n")
        stack = UndoOps.push(stack, newest)
        assertEquals(UndoOps.LIMIT, stack.size)
        assertEquals("H1", stack.first().root.children.single().title)
        assertEquals(newest, stack.last())
    }
}
