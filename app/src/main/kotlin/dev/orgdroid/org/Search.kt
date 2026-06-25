package dev.orgdroid.org

/**
 * Outline-aware substring search. Returns the set of [NodeId]s that should
 * be visible for [query]:
 *
 *   - A node is a "match" iff its title or any of its notes lines contains
 *     [query] (case-insensitive).
 *   - A node is "visible" iff it is a match OR any of its descendants is
 *     visible. This keeps ancestor context around each match.
 *
 * Returns the empty set for an empty query; callers should treat that as
 * "no filter, show everything" rather than "filter to nothing".
 *
 * The synthetic root's own title/notes are NOT searched: the root has no
 * row and treating it as searchable would force every descendant visible.
 */
object Search {
    fun visibleIds(root: Node, query: String): Set<NodeId> {
        if (query.isEmpty()) return emptySet()
        val needle = query.lowercase()
        val visible = mutableSetOf<NodeId>()
        for (c in root.children) {
            collect(c, needle, visible)
        }
        if (visible.isNotEmpty()) visible.add(root.id)
        return visible
    }

    private fun collect(node: Node, needle: String, out: MutableSet<NodeId>): Boolean {
        val selfMatch = node.title.lowercase().contains(needle) ||
            node.notes.any { it.lowercase().contains(needle) }
        var anyChildVisible = false
        for (c in node.children) {
            if (collect(c, needle, out)) anyChildVisible = true
        }
        val nodeVisible = selfMatch || anyChildVisible
        if (nodeVisible) out.add(node.id)
        return nodeVisible
    }
}
