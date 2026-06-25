package dev.orgdroid.outline

import dev.orgdroid.org.Node
import dev.orgdroid.org.NodeId

data class UndoSnapshot(
    val root: Node,
    val collapsed: Set<NodeId>,
    val focusedRoot: NodeId?,
    val dirty: Boolean,
)

object UndoOps {
    const val LIMIT = 50

    fun push(stack: List<UndoSnapshot>, snap: UndoSnapshot): List<UndoSnapshot> {
        if (stack.lastOrNull() == snap) return stack
        return (stack + snap).takeLast(LIMIT)
    }
}
