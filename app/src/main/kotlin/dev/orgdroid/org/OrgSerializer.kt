package dev.orgdroid.org

object OrgSerializer {

    fun serialize(root: Node): String {
        val sb = StringBuilder()
        emitLines(sb, root.notes)
        repeat(root.trailingBlankLines) { sb.append('\n') }
        for (child in root.children) emitNode(sb, child)
        return sb.toString()
    }

    private fun emitNode(sb: StringBuilder, node: Node) {
        sb.append(node.rawHeadingLine ?: reconstructHeading(node))
        sb.append('\n')
        emitLines(sb, node.notes)
        repeat(node.trailingBlankLines) { sb.append('\n') }
        for (child in node.children) emitNode(sb, child)
    }

    private fun emitLines(sb: StringBuilder, lines: List<String>) {
        for (line in lines) sb.append(line).append('\n')
    }

    private fun reconstructHeading(node: Node): String {
        val sb = StringBuilder()
        repeat(node.level) { sb.append('*') }
        sb.append(' ')
        node.todoState?.let { sb.append(it).append(' ') }
        node.priority?.let { sb.append("[#").append(it).append("] ") }
        sb.append(node.title)
        if (node.tags.isNotEmpty()) {
            sb.append(' ')
            sb.append(node.tags.joinToString(separator = ":", prefix = ":", postfix = ":"))
        }
        return sb.toString()
    }
}
