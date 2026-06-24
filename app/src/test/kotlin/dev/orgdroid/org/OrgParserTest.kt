package dev.orgdroid.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgParserTest {

    @Test fun emptyFile() {
        val root = OrgParser.parse("")
        assertEquals(0, root.level)
        assertEquals(0, root.children.size)
        assertEquals(0, root.notes.size)
    }

    @Test fun singleHeading() {
        val root = OrgParser.parse("* Hello\n")
        assertEquals(1, root.children.size)
        val h = root.children[0]
        assertEquals("Hello", h.title)
        assertEquals(1, h.level)
        assertNull(h.todoState)
        assertNull(h.priority)
        assertTrue(h.tags.isEmpty())
    }

    @Test fun nestedHeadings() {
        val text = """
            * One
            ** Two
            *** Three
            * Four
        """.trimIndent()
        val root = OrgParser.parse(text)
        assertEquals(2, root.children.size)
        val one = root.children[0]
        assertEquals("One", one.title)
        assertEquals(1, one.children.size)
        val two = one.children[0]
        assertEquals("Two", two.title)
        assertEquals(1, two.children.size)
        assertEquals("Three", two.children[0].title)
        assertEquals("Four", root.children[1].title)
    }

    @Test fun todoPriorityTags() {
        val root = OrgParser.parse("* TODO [#A] Do the thing :work:urgent:\n")
        val h = root.children[0]
        assertEquals("TODO", h.todoState)
        assertEquals('A', h.priority)
        assertEquals("Do the thing", h.title)
        assertEquals(listOf("work", "urgent"), h.tags)
    }

    @Test fun bodyTextBecomesNotes() {
        val text = """
            * Heading
            Body line one.
            - a list item
            More body.
        """.trimIndent()
        val root = OrgParser.parse(text)
        val h = root.children[0]
        assertEquals(listOf("Body line one.", "- a list item", "More body."), h.notes)
        assertTrue(h.children.isEmpty())
    }

    @Test fun drawerSkipsInnerHeadings() {
        val text = """
            * Outer
            :PROPERTIES:
            :ID: 123
            * NotAHeading
            :END:
            * RealSibling
        """.trimIndent()
        val root = OrgParser.parse(text)
        assertEquals(2, root.children.size)
        assertEquals("Outer", root.children[0].title)
        assertEquals("RealSibling", root.children[1].title)
        assertTrue(root.children[0].notes.contains(":PROPERTIES:"))
        assertTrue(root.children[0].notes.contains("* NotAHeading"))
    }

    @Test fun srcBlockSkipsInnerHeadings() {
        val text = """
            * Outer
            #+BEGIN_SRC kotlin
            * this is code
            #+END_SRC
            * Sibling
        """.trimIndent()
        val root = OrgParser.parse(text)
        assertEquals(2, root.children.size)
        assertEquals("Sibling", root.children[1].title)
    }

    @Test fun crlfInput() {
        val root = OrgParser.parse("* One\r\n** Two\r\n")
        assertEquals("One", root.children[0].title)
        assertEquals("Two", root.children[0].children[0].title)
    }

    @Test fun emptyTitleHeading() {
        val root = OrgParser.parse("* \nbody\n")
        assertEquals(1, root.children.size)
        assertEquals("", root.children[0].title)
        assertEquals(listOf("body"), root.children[0].notes)
    }

    @Test fun fileWithNoHeadings() {
        val root = OrgParser.parse("just\nsome\ntext\n")
        assertEquals(0, root.children.size)
        assertEquals(listOf("just", "some", "text"), root.notes)
    }

    @Test fun malformedTagDoesNotMatch() {
        val root = OrgParser.parse("* My heading :not tags:\n")
        val h = root.children[0]
        assertTrue(h.tags.isEmpty())
        assertEquals("My heading :not tags:", h.title)
    }

    @Test fun nodesHaveUniqueIds() {
        val root = OrgParser.parse("* A\n** B\n* C\n")
        val ids = mutableSetOf<Long>()
        fun collect(n: Node) {
            ids.add(n.id.value)
            n.children.forEach { collect(it) }
        }
        collect(root)
        // root + 3 headings = 4 unique ids
        assertEquals(4, ids.size)
    }

    @Test fun largeFileParsesQuickly() {
        val sb = StringBuilder()
        repeat(10_000) { sb.append("* H").append(it).append('\n') }
        val start = System.currentTimeMillis()
        val root = OrgParser.parse(sb.toString())
        val elapsed = System.currentTimeMillis() - start
        assertEquals(10_000, root.children.size)
        assertTrue("Parsing 10k headings took ${elapsed}ms", elapsed < 2000)
    }
}
