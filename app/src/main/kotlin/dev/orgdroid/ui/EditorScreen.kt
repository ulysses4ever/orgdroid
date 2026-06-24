package dev.orgdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orgdroid.editor.EditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.open(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.uri?.lastPathSegment ?: "orgdroid") },
                actions = {
                    TextButton(onClick = {
                        // text/* covers .org; include octet-stream as fallback for pickers that filter by extension
                        openLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                    }) { Text("Open") }
                    TextButton(
                        onClick = { vm.save() },
                        enabled = state.uri != null && state.dirty
                    ) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.error?.let {
                Text("Error: $it", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.error)
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                state.uri == null -> Text("No file open", Modifier.padding(16.dp))
                else -> BasicTextField(
                    value = state.text,
                    onValueChange = vm::onTextChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                )
            }
        }
    }
}
