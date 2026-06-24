package dev.orgdroid.org

data class OrgParseConfig(
    val todoKeywords: Set<String> = setOf("TODO", "DONE"),
)

/**
 * Line-based org-mode parser. Produces a synthetic-root tree of Node.
 *
 * Recognized at heading level:
 *   * TODO [#A] Title  :tag1:tag2:
 * Recognized as "opaque body" (skipped from heading detection):
 *   :PROPERTIES: ... :END: (and any :NAMED-DRAWER:)
 *   #+BEGIN_SRC ... #+END_SRC (any block type, case-insensitive)
 *
 * Drawers inside drawers / blocks inside blocks: not nested in M2; first matching close ends.
 */
object OrgParser {

    fun parse(text: String, config: OrgParseConfig = OrgParseConfig()): Node =
        ParseSession(config).run(text)

    private class ParseSession(val config: OrgParseConfig) {
        private var nextIdValue: Long = 1
        private fun newId() = NodeId(nextIdValue++)

        private val headingRe = Regex("""^(\*+)\s+(.*)$""")
        private val priorityRe = Regex("""^\[#([A-Z])\](?:\s+(.*))?$""")
        // Greedy on the title so trailing :tag(s): match correctly even when the title contains colons.
        private val tagBlockRe = Regex("""^(.*\S)\s+(:[\w@#%]+(?::[\w@#%]+)*:)\s*$""")
        private val drawerOpenRe = Regex("""^:[A-Za-z][\w-]*:\s*$""")
        private val blockOpenRe = Regex("""^\s*#\+BEGIN_(\w+)(?:\s.*)?$""", RegexOption.IGNORE_CASE)
        private val blockCloseRe = Regex("""^\s*#\+END_(\w+)\s*$""", RegexOption.IGNORE_CASE)
        private val drawerClose = ":END:"

        private class Builder(
            val id: NodeId,
            val level: Int,
            val title: String,
            val todoState: String?,
            val priority: Char?,
            val tags: List<String>,
            val rawHeadingLine: String?,
        ) {
            val notes: MutableList<String> = mutableListOf()
            val children: MutableList<Builder> = mutableListOf()
        }

        private data class ParsedHeading(
            val title: String,
            val todoState: String?,
            val priority: Char?,
            val tags: List<String>,
        )

        fun run(text: String): Node {
            val root = Builder(newId(), 0, "", null, null, emptyList(), null)
            val stack = ArrayDeque<Builder>()
            stack.addFirst(root)

            var inDrawer = false
            var inBlock: String? = null

            for (rawLine in text.lineSequence()) {
                val line = if (rawLine.endsWith('\r')) rawLine.dropLast(1) else rawLine

                if (inDrawer) {
                    stack.first().notes.add(line)
                    if (line.trim() == drawerClose) inDrawer = false
                    continue
                }
                if (inBlock != null) {
                    stack.first().notes.add(line)
                    val close = blockCloseRe.matchEntire(line)
                    if (close != null && close.groupValues[1].equals(inBlock, ignoreCase = true)) {
                        inBlock = null
                    }
                    continue
                }

                if (drawerOpenRe.matches(line) && line.trim() != drawerClose) {
                    stack.first().notes.add(line)
                    inDrawer = true
                    continue
                }
                val blockOpen = blockOpenRe.matchEntire(line)
                if (blockOpen != null) {
                    stack.first().notes.add(line)
                    inBlock = blockOpen.groupValues[1]
                    continue
                }
                val h = headingRe.matchEntire(line)
                if (h != null) {
                    val level = h.groupValues[1].length
                    val rest = h.groupValues[2]
                    val parsed = parseHeadingRest(rest)
                    while (stack.first().level >= level) stack.removeFirst()
                    val parent = stack.first()
                    val node = Builder(
                        id = newId(),
                        level = level,
                        title = parsed.title,
                        todoState = parsed.todoState,
                        priority = parsed.priority,
                        tags = parsed.tags,
                        rawHeadingLine = line,
                    )
                    parent.children.add(node)
                    stack.addFirst(node)
                    continue
                }
                stack.first().notes.add(line)
            }
            return build(root)
        }

        private fun parseHeadingRest(rest: String): ParsedHeading {
            var s = rest

            // TODO keyword: first whitespace-delimited token, if it's in the configured set
            var todoState: String? = null
            val sp = s.indexOf(' ')
            val firstToken = if (sp >= 0) s.substring(0, sp) else s
            if (firstToken in config.todoKeywords) {
                todoState = firstToken
                s = if (sp >= 0) s.substring(sp + 1).trimStart() else ""
            }

            // Priority [#X]
            var priority: Char? = null
            val pm = priorityRe.matchEntire(s)
            if (pm != null) {
                priority = pm.groupValues[1][0]
                s = pm.groupValues[2]
            }

            // Trailing :tag(s): block
            var tags: List<String> = emptyList()
            val tm = tagBlockRe.matchEntire(s)
            if (tm != null) {
                tags = tm.groupValues[2].trim(':').split(':').filter { it.isNotEmpty() }
                s = tm.groupValues[1]
            }

            return ParsedHeading(s.trim(), todoState, priority, tags)
        }

        private fun build(b: Builder): Node {
            var blanks = 0
            while (b.notes.isNotEmpty() && b.notes.last().isBlank()) {
                b.notes.removeAt(b.notes.size - 1)
                blanks++
            }
            return Node(
                id = b.id,
                level = b.level,
                title = b.title,
                todoState = b.todoState,
                priority = b.priority,
                tags = b.tags,
                notes = b.notes.toList(),
                children = b.children.map { build(it) },
                rawHeadingLine = b.rawHeadingLine,
                trailingBlankLines = blanks,
            )
        }
    }
}
