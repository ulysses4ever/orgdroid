package dev.orgdroid.outline

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orgdroid.file.FileIo
import dev.orgdroid.org.Node
import dev.orgdroid.org.NodeId
import dev.orgdroid.org.OrgParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OutlineState(
    val uri: Uri? = null,
    val fileName: String? = null,
    val root: Node? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val collapsed: Set<NodeId> = emptySet(),
)

class OutlineViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(OutlineState())
    val state: StateFlow<OutlineState> = _state

    fun open(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        FileIo.persistPermission(resolver, uri)
        val name = uri.lastPathSegment?.substringAfterLast('/')
        _state.value = OutlineState(uri = uri, fileName = name, loading = true)
        viewModelScope.launch {
            try {
                val text = FileIo.read(resolver, uri)
                val root = withContext(Dispatchers.Default) { OrgParser.parse(text) }
                _state.value = OutlineState(uri = uri, fileName = name, root = root)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun toggleCollapse(id: NodeId) {
        val s = _state.value
        val newCollapsed = if (id in s.collapsed) s.collapsed - id else s.collapsed + id
        _state.value = s.copy(collapsed = newCollapsed)
    }
}
