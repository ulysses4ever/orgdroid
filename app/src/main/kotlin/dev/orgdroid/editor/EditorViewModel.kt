package dev.orgdroid.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orgdroid.file.FileIo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class EditorState(
    val uri: Uri? = null,
    val text: String = "",
    val loading: Boolean = false,
    val dirty: Boolean = false,
    val error: String? = null,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    fun open(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        FileIo.persistPermission(resolver, uri)
        _state.value = _state.value.copy(uri = uri, loading = true, error = null)
        viewModelScope.launch {
            try {
                val text = FileIo.read(resolver, uri)
                _state.value = EditorState(uri = uri, text = text, loading = false, dirty = false)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message)
            }
        }
    }

    fun onTextChange(new: String) {
        val s = _state.value
        if (new != s.text) _state.value = s.copy(text = new, dirty = true)
    }

    fun save() {
        val s = _state.value
        val uri = s.uri ?: return
        val resolver = getApplication<Application>().contentResolver
        viewModelScope.launch {
            try {
                FileIo.write(resolver, uri, s.text)
                _state.value = s.copy(dirty = false)
            } catch (t: Throwable) {
                _state.value = s.copy(error = t.message)
            }
        }
    }
}
