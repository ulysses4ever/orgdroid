package dev.orgdroid.org

@JvmInline
value class NodeId(val value: Long)

/**
 * Immutable parsed org node.
 *
 * `notes` holds the body lines verbatim between this heading and the next heading of equal
 * or lower level. Plain lists (`- foo`), drawers, and code blocks stay as text inside notes —
 * they do not become children. This preserves round-trip fidelity for the M3 serializer.
 *
 * `rawHeadingLine` is the original heading line, kept so M3 can re-emit it unchanged when
 * the parsed parts (title/tags/todo/priority) haven't been edited.
 */
data class Node(
    val id: NodeId,
    val level: Int,                  // 0 = synthetic root, 1..N for headings
    val title: String,
    val todoState: String?,          // "TODO" | "DONE" | null
    val priority: Char?,             // 'A'..'Z'
    val tags: List<String>,
    val notes: List<String>,
    val children: List<Node>,
    val rawHeadingLine: String?,     // null for synthetic root
    val trailingBlankLines: Int,
) {
    val hasChildren: Boolean get() = children.isNotEmpty()
}
