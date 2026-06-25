package dev.orgdroid.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    private fun tree(): Node = OrgParser.parse(
        """
            * Project Alpha
            ** Design doc
            ** Notes
            done with this
            * Project Beta
            ** TODO ship it
            * Meeting notes
        """.trimIndent() + "\n"
    )

    @Test fun emptyQueryReturnsEmptySet() {
        val root = tree()
        assertTrue(Search.visibleIds(root, "").isEmpty())
    }

    @Test fun matchOnTitle() {
        val root = tree()
        val visible = Search.visibleIds(root, "Beta")
        val byTitle = visible.mapNotNull { TreeOps.findNode(root, it)?.title }.toSet()
        assertTrue("Project Beta" in byTitle)
        assertTrue(root.id in visible)
        assertFalse("Project Alpha" in byTitle)
        assertFalse("Meeting notes" in byTitle)
    }

    @Test fun matchInsideNotesBubbleUpToAncestor() {
        val root = tree()
        val visible = Search.visibleIds(root, "done with")
        val byTitle = visible.mapNotNull { TreeOps.findNode(root, it)?.title }.toSet()
        assertTrue("Notes" in byTitle)
        assertTrue("Project Alpha" in byTitle)
        assertFalse("Project Beta" in byTitle)
    }

    @Test fun caseInsensitiveSubstring() {
        val root = tree()
        val a = Search.visibleIds(root, "ALPHA")
        val b = Search.visibleIds(root, "alpha")
        assertEquals(a, b)
    }

    @Test fun noMatchReturnsEmpty() {
        val root = tree()
        assertTrue(Search.visibleIds(root, "zzz-no-such").isEmpty())
    }

    @Test fun rootIncludedWhenAnyDescendantMatches() {
        val root = tree()
        val visible = Search.visibleIds(root, "Project")
        assertTrue(root.id in visible)
    }

    @Test fun descendantsOfMatchAreNotAutoIncluded() {
        val root = tree()
        val visible = Search.visibleIds(root, "Project Alpha")
        val titles = visible.mapNotNull { TreeOps.findNode(root, it)?.title }.toSet()
        assertTrue("Project Alpha" in titles)
        assertFalse("Design doc" in titles)
        assertFalse("Notes" in titles)
    }
}
