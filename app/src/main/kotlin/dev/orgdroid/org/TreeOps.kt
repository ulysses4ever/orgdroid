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

    fun findParentAndIndex(root: Node, id: NodeId): Pair<Node, Int>? {
        for ((i, c) in root.children.withIndex()) {
            if (c.id == id) return root to i
            val sub = findParentAndIndex(c, id)
            if (sub != null) return sub
        }
        return null
    }

    private fun bumpLevels(node: Node, delta: Int): Node = node.copy(
        level = node.level + delta,
        rawHeadingLine = null,
        children = node.children.map { bumpLevels(it, delta) },
    )

    fun indent(root: Node, id: NodeId): Node {
        val (parent, idx) = findParentAndIndex(root, id) ?: return root
        if (idx == 0) return root
        val node = parent.children[idx]
        val prev = parent.children[idx - 1]
        val bumped = bumpLevels(node, +1)
        val newChildren = parent.children.toMutableList().apply {
            set(idx - 1, prev.copy(children = prev.children + bumped))
            removeAt(idx)
        }
        return transform(root, parent.id) { it.copy(children = newChildren) }
    }

    fun outdent(root: Node, id: NodeId, focusedRoot: NodeId? = null): Node {
        val (parent, idx) = findParentAndIndex(root, id) ?: return root
        val ceiling = focusedRoot ?: root.id
        if (parent.id == ceiling) return root
        val (grandparent, parentIdx) = findParentAndIndex(root, parent.id) ?: return root
        val node = parent.children[idx]
        val newParent = parent.copy(
            children = parent.children.toMutableList().apply { removeAt(idx) }
        )
        val bumped = bumpLevels(node, -1)
        val newGrandChildren = grandparent.children.toMutableList().apply {
            set(parentIdx, newParent)
            add(parentIdx + 1, bumped)
        }
        return transform(root, grandparent.id) { it.copy(children = newGrandChildren) }
    }

    fun moveUp(root: Node, id: NodeId): Node {
        val (parent, idx) = findParentAndIndex(root, id) ?: return root
        if (idx == 0) return root
        val newChildren = parent.children.toMutableList().apply {
            val node = removeAt(idx)
            add(idx - 1, node)
        }
        return transform(root, parent.id) { it.copy(children = newChildren) }
    }

    fun moveDown(root: Node, id: NodeId): Node {
        val (parent, idx) = findParentAndIndex(root, id) ?: return root
        if (idx >= parent.children.size - 1) return root
        val newChildren = parent.children.toMutableList().apply {
            val node = removeAt(idx)
            add(idx + 1, node)
        }
        return transform(root, parent.id) { it.copy(children = newChildren) }
    }

    fun updateTodoState(root: Node, id: NodeId, newState: String?): Node =
        transform(root, id) { it.copy(todoState = newState, rawHeadingLine = null) }

    fun cycleTodoState(root: Node, id: NodeId): Node {
        val node = findNode(root, id) ?: return root
        val next = when (node.todoState) {
            null -> "TODO"
            "TODO" -> "DONE"
            "DONE" -> null
            else -> null
        }
        return updateTodoState(root, id, next)
    }

    fun updateNotes(root: Node, id: NodeId, newNotes: List<String>): Node =
        transform(root, id) { it.copy(notes = newNotes) }

    fun updatePriority(root: Node, id: NodeId, priority: Char?): Node =
        transform(root, id) { it.copy(priority = priority, rawHeadingLine = null) }

    fun updateTags(root: Node, id: NodeId, tags: List<String>): Node =
        transform(root, id) { it.copy(tags = tags, rawHeadingLine = null) }
}
