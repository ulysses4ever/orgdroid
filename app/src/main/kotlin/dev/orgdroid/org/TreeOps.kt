package dev.orgdroid.org

object TreeOps {

    fun findNode(root: Node, id: NodeId): Node? {
        if (root.id == id) return root
        for (c in root.children) findNode(c, id)?.let { return it }
        return null
    }

    fun transform(root: Node, id: NodeId, transform: (Node) -> Node?): Node {
        if (root.id == id) {
            return transform(root) ?: error("Cannot delete the synthetic root")
        }
        return walk(root, id, transform) ?: error("Walk lost the root somehow")
    }

    private fun walk(node: Node, id: NodeId, t: (Node) -> Node?): Node? {
        if (node.id == id) return t(node)
        var changed = false
        val newChildren = mutableListOf<Node>()
        for (child in node.children) {
            val res = walk(child, id, t)
            if (res !== child) changed = true
            if (res != null) newChildren.add(res)
        }
        return if (changed) node.copy(children = newChildren) else node
    }

    fun updateTitle(root: Node, id: NodeId, newTitle: String): Node =
        transform(root, id) { it.copy(title = newTitle, rawHeadingLine = null) }

    fun delete(root: Node, id: NodeId): Node = transform(root, id) { null }

    fun insertSiblingAfter(
        root: Node,
        afterId: NodeId,
        newNodeIdValue: Long,
    ): Pair<Node, NodeId> {
        val target = findNode(root, afterId) ?: error("No node with id $afterId")
        val newId = NodeId(newNodeIdValue)
        val newNode = Node(
            id = newId,
            level = target.level,
            title = "",
            todoState = null,
            priority = null,
            tags = emptyList(),
            notes = emptyList(),
            children = emptyList(),
            rawHeadingLine = null,
            trailingBlankLines = 0,
        )
        return Pair(insertAfter(root, afterId, newNode), newId)
    }

    private fun insertAfter(node: Node, afterId: NodeId, newNode: Node): Node {
        var changed = false
        val newChildren = mutableListOf<Node>()
        for (child in node.children) {
            if (child.id == afterId) {
                newChildren.add(child)
                newChildren.add(newNode)
                changed = true
            } else {
                val updated = insertAfter(child, afterId, newNode)
                if (updated !== child) changed = true
                newChildren.add(updated)
            }
        }
        return if (changed) node.copy(children = newChildren) else node
    }

    fun appendTopLevel(root: Node, newNodeIdValue: Long): Pair<Node, NodeId> {
        val newId = NodeId(newNodeIdValue)
        val newNode = Node(
            id = newId,
            level = 1,
            title = "",
            todoState = null,
            priority = null,
            tags = emptyList(),
            notes = emptyList(),
            children = emptyList(),
            rawHeadingLine = null,
            trailingBlankLines = 0,
        )
        return Pair(root.copy(children = root.children + newNode), newId)
    }

    fun maxNodeIdValue(root: Node): Long {
        var max = root.id.value
        for (c in root.children) {
            val m = maxNodeIdValue(c)
            if (m > max) max = m
        }
        return max
    }
}
