package dev.orgdroid.org

import org.junit.Assert.assertEquals
import org.junit.Test

class OrgSerializerTest {

    @Test fun emptyTree() {
        val root = OrgParser.parse("")
        assertEquals("", OrgSerializer.serialize(root))
    }

    @Test fun rootNotesOnly() {
        val input = "#+TITLE: foo\nbody\n"
        val root = OrgParser.parse(input)
        assertEquals(input, OrgSerializer.serialize(root))
    }

    @Test fun singleHeadingRoundTrip() {
        val input = "* Hello\n"
        val root = OrgParser.parse(input)
        assertEquals(input, OrgSerializer.serialize(root))
    }

    @Test fun nestedHeadingsRoundTrip() {
        val input = "* One\n** Two\n*** Three\n* Four\n"
        val root = OrgParser.parse(input)
        assertEquals(input, OrgSerializer.serialize(root))
    }

    @Test fun headingWithNotesRoundTrip() {
        val input = "* H\nbody line one\n- list\n* Sib\n"
        val root = OrgParser.parse(input)
        assertEquals(input, OrgSerializer.serialize(root))
    }

    @Test fun headingWithTodoPriorityTagsRoundTrip() {
        val input = "* TODO [#A] Title :work:urgent:\n"
        val root = OrgParser.parse(input)
        assertEquals(input, OrgSerializer.serialize(root))
    }

    @Test fun updatedTitleReconstructsHeadingLine() {
        val input = "* TODO [#A] Original :tag:\n"
        val root = OrgParser.parse(input)
        val one = root.children[0]
        val mutated = TreeOps.updateTitle(root, one.id, "New title")
        val out = OrgSerializer.serialize(mutated)
        assertEquals("* TODO [#A] New title :tag:\n", out)
    }

    @Test fun emptyTitleHeadingRoundTrip() {
        val input = "* \nbody\n"
        val root = OrgParser.parse(input)
        assertEquals(input, OrgSerializer.serialize(root))
    }

    @Test fun reconstructedEmptyTitleHasNoTrailingSpace() {
        val input = "* foo\n"
        val root = OrgParser.parse(input)
        val mutated = TreeOps.updateTitle(root, root.children[0].id, "")
        assertEquals("* \n", OrgSerializer.serialize(mutated))
    }

    @Test fun appendThenSerialize() {
        val input = "* One\n"
        val root = OrgParser.parse(input)
        val (mutated, _) = TreeOps.appendTopLevel(root, 999L)
        assertEquals("* One\n* \n", OrgSerializer.serialize(mutated))
    }

    @Test fun notesEditRoundTrips() {
        val input = "* Foo\noriginal body\n"
        val root = OrgParser.parse(input)
        val mutated = TreeOps.updateNotes(root, root.children[0].id, listOf("edited", "two lines"))
        assertEquals("* Foo\nedited\ntwo lines\n", OrgSerializer.serialize(mutated))
    }

    @Test fun todoStateChangeReconstructsHeading() {
        val input = "* TODO Foo\n"
        val root = OrgParser.parse(input)
        val mutated = TreeOps.updateTodoState(root, root.children[0].id, "DONE")
        assertEquals("* DONE Foo\n", OrgSerializer.serialize(mutated))
    }

    @Test fun todoStateRemovalReconstructsHeading() {
        val input = "* DONE Foo :tag:\n"
        val root = OrgParser.parse(input)
        val mutated = TreeOps.updateTodoState(root, root.children[0].id, null)
        assertEquals("* Foo :tag:\n", OrgSerializer.serialize(mutated))
    }

    @Test fun moveUpRoundTripsByteIdenticalForMovedSubtree() {
        val input = "* One\n** OneA\nbody of OneA\n** OneB\n* Two\nbody of Two\n* Three\n"
        val root = OrgParser.parse(input)
        val two = root.children[1]
        val mutated = TreeOps.moveUp(root, two.id)
        val expected = "* Two\nbody of Two\n* One\n** OneA\nbody of OneA\n** OneB\n* Three\n"
        assertEquals(expected, OrgSerializer.serialize(mutated))
    }
}
