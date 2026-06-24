package dev.orgdroid.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeOpsTest {

    private fun tree(): Node = OrgParser.parse(
        """
            * One
            ** OneA
            ** OneB
            * Two
            * Three
        """.trimIndent() + "\n"
    )

    @Test fun findNodeFindsNested() {
        val root = tree()
        val oneA = root.children[0].children[0]
        val found = TreeOps.findNode(root, oneA.id)
        assertNotNull(found)
        assertEquals("OneA", found!!.title)
    }

    @Test fun findNodeReturnsNullForUnknown() {
        val root = tree()
        assertNull(TreeOps.findNode(root, NodeId(99999L)))
    }

    @Test fun updateTitleChangesTitleAndClearsRaw() {
        val root = tree()
        val one = root.children[0]
        val newRoot = TreeOps.updateTitle(root, one.id, "Renamed")
        val updated = TreeOps.findNode(newRoot, one.id)!!
        assertEquals("Renamed", updated.title)
        assertNull(updated.rawHeadingLine)
    }

    @Test fun updateTitlePreservesOtherSubtreeIds() {
        val root = tree()
        val two = root.children[1]
        val three = root.children[2]
        val newRoot = TreeOps.updateTitle(root, root.children[0].id, "Renamed")
        assertTrue(newRoot.children[1] === two)
        assertTrue(newRoot.children[2] === three)
    }

    @Test fun insertSiblingAfterAtTopLevel() {
        val root = tree()
        val one = root.children[0]
        val (newRoot, newId) = TreeOps.insertSiblingAfter(root, one.id, 1000L)
        assertEquals(4, newRoot.children.size)
        assertEquals(one.id, newRoot.children[0].id)
        assertEquals(newId, newRoot.children[1].id)
        assertEquals("", newRoot.children[1].title)
        assertEquals(1, newRoot.children[1].level)
    }

    @Test fun insertSiblingAfterInNestedSubtree() {
        val root = tree()
        val oneA = root.children[0].children[0]
        val (newRoot, newId) = TreeOps.insertSiblingAfter(root, oneA.id, 2000L)
        val one = newRoot.children[0]
        assertEquals(3, one.children.size)
        assertEquals(oneA.id, one.children[0].id)
        assertEquals(newId, one.children[1].id)
        assertEquals(2, one.children[1].level)
    }

    @Test fun deleteRemovesNodeAndChildren() {
        val root = tree()
        val one = root.children[0]
        val newRoot = TreeOps.delete(root, one.id)
        assertEquals(2, newRoot.children.size)
        assertEquals("Two", newRoot.children[0].title)
        assertEquals("Three", newRoot.children[1].title)
        assertNull(TreeOps.findNode(newRoot, one.children[0].id))
    }

    @Test fun appendTopLevelAddsAtEnd() {
        val root = tree()
        val (newRoot, newId) = TreeOps.appendTopLevel(root, 3000L)
        assertEquals(4, newRoot.children.size)
        assertEquals(newId, newRoot.children[3].id)
        assertEquals(1, newRoot.children[3].level)
    }

    @Test fun maxNodeIdValueWalksWholeTree() {
        val root = tree()
        val max = TreeOps.maxNodeIdValue(root)
        assertEquals(6L, max)
    }

    @Test fun findParentAndIndexAtTopLevel() {
        val root = tree()
        val two = root.children[1]
        val (parent, idx) = TreeOps.findParentAndIndex(root, two.id)!!
        assertEquals(root.id, parent.id)
        assertEquals(1, idx)
    }

    @Test fun findParentAndIndexNested() {
        val root = tree()
        val oneB = root.children[0].children[1]
        val (parent, idx) = TreeOps.findParentAndIndex(root, oneB.id)!!
        assertEquals(root.children[0].id, parent.id)
        assertEquals(1, idx)
    }

    @Test fun findParentAndIndexRootReturnsNull() {
        val root = tree()
        assertNull(TreeOps.findParentAndIndex(root, root.id))
    }

    @Test fun indentMakesPreviousSiblingTheParent() {
        val root = tree()
        val two = root.children[1]
        val newRoot = TreeOps.indent(root, two.id)
        assertEquals(2, newRoot.children.size)
        assertEquals(3, newRoot.children[0].children.size)
        val indented = TreeOps.findNode(newRoot, two.id)!!
        assertEquals(2, indented.level)
        assertNull(indented.rawHeadingLine)
    }

    @Test fun indentFirstChildIsNoOp() {
        val root = tree()
        val one = root.children[0]
        val newRoot = TreeOps.indent(root, one.id)
        assertTrue(newRoot === root)
    }

    @Test fun outdentMovesNodeUpOneLevel() {
        val root = tree()
        val oneA = root.children[0].children[0]
        val newRoot = TreeOps.outdent(root, oneA.id)
        assertEquals(4, newRoot.children.size)
        assertEquals(oneA.id, newRoot.children[1].id)
        val moved = TreeOps.findNode(newRoot, oneA.id)!!
        assertEquals(1, moved.level)
        assertNull(moved.rawHeadingLine)
    }

    @Test fun outdentAtTopLevelIsNoOp() {
        val root = tree()
        val one = root.children[0]
        val newRoot = TreeOps.outdent(root, one.id)
        assertTrue(newRoot === root)
    }

    @Test fun outdentRespectsFocusedRoot() {
        val root = tree()
        val one = root.children[0]
        val oneA = one.children[0]
        val newRoot = TreeOps.outdent(root, oneA.id, focusedRoot = one.id)
        assertTrue(newRoot === root)
    }

    @Test fun indentBumpsDescendantLevels() {
        val root = OrgParser.parse("* One\n** OneA\n* Two\n** TwoA\n*** TwoAA\n")
        val two = root.children[1]
        val newRoot = TreeOps.indent(root, two.id)
        val indentedTwo = TreeOps.findNode(newRoot, two.id)!!
        assertEquals(2, indentedTwo.level)
        assertEquals(3, indentedTwo.children[0].level)
        assertEquals(4, indentedTwo.children[0].children[0].level)
    }

    @Test fun outdentBumpsDescendantLevels() {
        val root = OrgParser.parse("* One\n** OneA\n*** OneAA\n")
        val oneA = root.children[0].children[0]
        val newRoot = TreeOps.outdent(root, oneA.id)
        val movedOneA = newRoot.children.first { it.title == "OneA" }
        assertEquals(1, movedOneA.level)
        assertEquals(2, movedOneA.children[0].level)
        assertNull(movedOneA.children[0].rawHeadingLine)
    }
}
