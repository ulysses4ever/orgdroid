package dev.orgdroid.outline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orgdroid.file.FileIo
import dev.orgdroid.org.Node
import dev.orgdroid.org.NodeId
import dev.orgdroid.org.OrgParser
import dev.orgdroid.org.OrgSerializer
import dev.orgdroid.org.TreeOps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OutlineState(
    val uri: Uri? = null,
    val fileName: String? = null,
    val root: Node? = null,
    val originalText: String? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val collapsed: Set<NodeId> = emptySet(),
    val editing: NodeId? = null,
    val editBuffer: String = "",
    val editingNotes: NodeId? = null,
    val notesBuffer: String = "",
    val dirty: Boolean = false,
    val conflictPending: Boolean = false,
    val focusedRoot: NodeId? = null,
)

class OutlineViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(OutlineState())
    val state: StateFlow<OutlineState> = _state

    private var nextNodeIdValue: Long = 1L

    fun open(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        FileIo.persistPermission(resolver, uri)
        val name = uri.lastPathSegment?.substringAfterLast('/')
        _state.value = OutlineState(uri = uri, fileName = name, loading = true)
        viewModelScope.launch {
            try {
                val text = FileIo.read(resolver, uri)
                val root = withContext(Dispatchers.Default) { OrgParser.parse(text) }
                nextNodeIdValue = TreeOps.maxNodeIdValue(root) + 1L
                _state.value = OutlineState(
                    uri = uri,
                    fileName = name,
                    root = root,
                    originalText = text,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun toggleCollapse(id: NodeId) {
        val s = commitEditInternal(_state.value)
        val newCollapsed = if (id in s.collapsed) s.collapsed - id else s.collapsed + id
        _state.value = s.copy(collapsed = newCollapsed)
    }

    fun beginEdit(id: NodeId) {
        val s = _state.value
        val root = s.root ?: return
        if (s.editing == id) return
        val committed = commitEditInternal(s)
        val node = TreeOps.findNode(committed.root!!, id) ?: return
        _state.value = committed.copy(editing = id, editBuffer = node.title)
    }

    fun onEditBufferChange(text: String) {
        val s = _state.value
        if (s.editing == null) return
        _state.value = s.copy(editBuffer = text)
    }

    fun commitEdit() {
        _state.value = commitEditInternal(_state.value)
    }

    private fun commitEditInternal(s: OutlineState): OutlineState {
        var current = s
        val editingId = current.editing
        if (editingId != null) {
            val root = current.root
            if (root != null) {
                val node = TreeOps.findNode(root, editingId)
                if (node != null && node.title != current.editBuffer) {
                    current = current.copy(
                        root = TreeOps.updateTitle(root, editingId, current.editBuffer),
                        dirty = true,
                    )
                }
            }
            current = current.copy(editing = null, editBuffer = "")
        }
        val notesId = current.editingNotes
        if (notesId != null) {
            val root = current.root
            if (root != null) {
                val node = TreeOps.findNode(root, notesId)
                if (node != null) {
                    val newNotes = if (current.notesBuffer.isEmpty()) emptyList()
                                   else current.notesBuffer.split('\n')
                    if (newNotes != node.notes) {
                        current = current.copy(
                            root = TreeOps.updateNotes(root, notesId, newNotes),
                            dirty = true,
                        )
                    }
                }
            }
            current = current.copy(editingNotes = null, notesBuffer = "")
        }
        return current
    }

    fun cancelEdit() {
        _state.value = _state.value.copy(editing = null, editBuffer = "")
    }

    fun beginEditNotes(id: NodeId) {
        val s = _state.value
        val root = s.root ?: return
        if (s.editingNotes == id) return
        val committed = commitEditInternal(s)
        val node = TreeOps.findNode(committed.root!!, id) ?: return
        val buf = node.notes.joinToString("\n")
        _state.value = committed.copy(editingNotes = id, notesBuffer = buf)
    }

    fun onNotesBufferChange(text: String) {
        val s = _state.value
        if (s.editingNotes == null) return
        _state.value = s.copy(notesBuffer = text)
    }

    fun commitNotes() {
        _state.value = commitEditInternal(_state.value)
    }

    fun cancelNotes() {
        _state.value = _state.value.copy(editingNotes = null, notesBuffer = "")
    }

    fun cycleTodo(id: NodeId) {
        val s = commitEditInternal(_state.value)
        val root = s.root ?: return
        TreeOps.findNode(root, id) ?: return
        val newRoot = TreeOps.cycleTodoState(root, id)
        if (newRoot === root) return
        _state.value = s.copy(root = newRoot, dirty = true)
    }

    fun createSiblingAfter(id: NodeId) {
        val s = _state.value
        val committed = commitEditInternal(s)
        val root = committed.root ?: return
        val (newRoot, newId) = TreeOps.insertSiblingAfter(root, id, nextNodeIdValue++)
        _state.value = committed.copy(
            root = newRoot,
            dirty = true,
            editing = newId,
            editBuffer = "",
        )
    }

    fun appendInScope() {
        val s = _state.value
        val committed = commitEditInternal(s)
        val root = committed.root ?: return
        val (newRoot, newId) = if (committed.focusedRoot != null) {
            val focused = TreeOps.findNode(root, committed.focusedRoot!!) ?: return
            val newId = NodeId(nextNodeIdValue++)
            val newNode = Node(
                id = newId,
                level = focused.level + 1,
                title = "",
                todoState = null,
                priority = null,
                tags = emptyList(),
                notes = emptyList(),
                children = emptyList(),
                rawHeadingLine = null,
                trailingBlankLines = 0,
            )
            val updated = TreeOps.transform(root, focused.id) {
                it.copy(children = it.children + newNode)
            }
            updated to newId
        } else {
            TreeOps.appendTopLevel(root, nextNodeIdValue++)
        }
        _state.value = committed.copy(
            root = newRoot,
            dirty = true,
            editing = newId,
            editBuffer = "",
        )
    }

    fun delete(id: NodeId) {
        val s = _state.value
        val root = s.root ?: return
        if (root.id == id) return
        val newRoot = TreeOps.delete(root, id)
        val validFocused = s.focusedRoot?.takeIf { TreeOps.findNode(newRoot, it) != null }
        _state.value = s.copy(
            root = newRoot,
            dirty = true,
            editing = if (s.editing == id) null else s.editing,
            editBuffer = if (s.editing == id) "" else s.editBuffer,
            editingNotes = if (s.editingNotes == id) null else s.editingNotes,
            notesBuffer = if (s.editingNotes == id) "" else s.notesBuffer,
            collapsed = s.collapsed - id,
            focusedRoot = validFocused,
        )
    }

    fun moveUp(id: NodeId) {
        val s = commitEditInternal(_state.value)
        val root = s.root ?: return
        val newRoot = TreeOps.moveUp(root, id)
        if (newRoot === root) return
        _state.value = s.copy(root = newRoot, dirty = true)
    }

    fun moveDown(id: NodeId) {
        val s = commitEditInternal(_state.value)
        val root = s.root ?: return
        val newRoot = TreeOps.moveDown(root, id)
        if (newRoot === root) return
        _state.value = s.copy(root = newRoot, dirty = true)
    }

    fun indent(id: NodeId) {
        val s = commitEditInternal(_state.value)
        val root = s.root ?: return
        val newRoot = TreeOps.indent(root, id)
        if (newRoot === root) return
        _state.value = s.copy(root = newRoot, dirty = true)
    }

    fun outdent(id: NodeId) {
        val s = commitEditInternal(_state.value)
        val root = s.root ?: return
        val newRoot = TreeOps.outdent(root, id, focusedRoot = s.focusedRoot)
        if (newRoot === root) return
        _state.value = s.copy(root = newRoot, dirty = true)
    }

    fun zoomIn(id: NodeId) {
        val s = commitEditInternal(_state.value)
        val root = s.root ?: return
        val node = TreeOps.findNode(root, id) ?: return
        if (node.children.isEmpty()) return
        _state.value = s.copy(focusedRoot = id)
    }

    fun zoomOut() {
        val s = commitEditInternal(_state.value)
        val root = s.root ?: return
        val current = s.focusedRoot ?: return
        val parent = TreeOps.findParentAndIndex(root, current)?.first
        val newFocus = if (parent == null || parent.id == root.id) null else parent.id
        _state.value = s.copy(focusedRoot = newFocus)
    }

    fun save() {
        val s = _state.value
        val uri = s.uri ?: return
        if (s.saving) return
        val original = s.originalText ?: return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val onDisk = FileIo.read(resolver, uri)
                if (onDisk != original) {
                    _state.value = _state.value.copy(saving = false, conflictPending = true)
                    return@launch
                }
                performSave()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(saving = false, error = t.message)
            }
        }
    }

    fun confirmOverwrite() {
        viewModelScope.launch { performSave() }
    }

    fun discardLocal() {
        val s = _state.value
        val uri = s.uri ?: return
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                val text = FileIo.read(resolver, uri)
                val newRoot = withContext(Dispatchers.Default) { OrgParser.parse(text) }
                nextNodeIdValue = TreeOps.maxNodeIdValue(newRoot) + 1L
                _state.value = s.copy(
                    root = newRoot,
                    originalText = text,
                    dirty = false,
                    editing = null,
                    editBuffer = "",
                    editingNotes = null,
                    notesBuffer = "",
                    collapsed = emptySet(),
                    conflictPending = false,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message, conflictPending = false)
            }
        }
    }

    fun dismissConflict() {
        _state.value = _state.value.copy(conflictPending = false)
    }

    private suspend fun performSave() {
        val resolver = getApplication<Application>().contentResolver
        val committed = commitEditInternal(_state.value)
        val root = committed.root ?: return
        val uri = committed.uri ?: return
        _state.value = committed.copy(saving = true, conflictPending = false)
        try {
            val text = withContext(Dispatchers.Default) { OrgSerializer.serialize(root) }
            FileIo.write(resolver, uri, text)
            _state.value = _state.value.copy(
                originalText = text,
                dirty = false,
                saving = false,
            )
        } catch (t: Throwable) {
            _state.value = _state.value.copy(saving = false, error = t.message)
        }
    }
}
