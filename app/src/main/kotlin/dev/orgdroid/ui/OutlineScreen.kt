package dev.orgdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.orgdroid.org.Node
import dev.orgdroid.org.NodeId
import dev.orgdroid.outline.OutlineViewModel

private data class RenderItem(
    val node: Node,
    val depth: Int,
    val hasChildren: Boolean,
    val isCollapsed: Boolean,
)

private fun flatten(root: Node?, collapsed: Set<NodeId>): List<RenderItem> {
    if (root == null) return emptyList()
    val out = mutableListOf<RenderItem>()
    fun visit(node: Node, depth: Int) {
        val isCollapsed = node.id in collapsed
        if (node.level > 0) {
            out.add(RenderItem(node, depth, node.children.isNotEmpty(), isCollapsed))
        }
        if (!isCollapsed) {
            val childDepth = if (node.level == 0) 0 else depth + 1
            for (child in node.children) visit(child, childDepth)
        }
    }
    visit(root, 0)
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(vm: OutlineViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.open(uri) }

    val items by remember(state.root, state.collapsed) {
        derivedStateOf { flatten(state.root, state.collapsed) }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(state.editing) {
        val id = state.editing ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.node.id == id }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = state.fileName ?: "orgdroid"
                    Text(if (state.dirty) "$name •" else name)
                },
                actions = {
                    TextButton(onClick = {
                        openLauncher.launch(arrayOf("text/*", "application/octet-stream"))
                    }) { Text("Open") }
                    TextButton(
                        onClick = { vm.save() },
                        enabled = state.dirty && !state.saving,
                    ) { Text(if (state.saving) "Saving…" else "Save") }
                }
            )
        },
        floatingActionButton = {
            if (state.root != null) {
                FloatingActionButton(onClick = { vm.appendTopLevel() }) {
                    Icon(Icons.Filled.Add, contentDescription = "New heading")
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                state.error != null -> Text(
                    "Error: ${state.error}",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                state.root == null -> Text("No file open. Tap Open.", Modifier.padding(16.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                    items(items, key = { it.node.id.value }) { item ->
                        OutlineRow(
                            item = item,
                            editing = state.editing == item.node.id,
                            editBuffer = state.editBuffer,
                            onToggle = { vm.toggleCollapse(item.node.id) },
                            onBeginEdit = { vm.beginEdit(item.node.id) },
                            onEditChange = vm::onEditBufferChange,
                            onCommitEdit = { vm.commitEdit() },
                            onEnterCreatesSibling = { vm.createSiblingAfter(item.node.id) },
                            onDelete = { vm.delete(item.node.id) },
                        )
                    }
                }
            }
        }
    }

    if (state.conflictPending) {
        ConflictDialog(
            onOverwrite = { vm.confirmOverwrite() },
            onDiscard = { vm.discardLocal() },
            onDismiss = { vm.dismissConflict() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlineRow(
    item: RenderItem,
    editing: Boolean,
    editBuffer: String,
    onToggle: () -> Unit,
    onBeginEdit: () -> Unit,
    onEditChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onEnterCreatesSibling: () -> Unit,
    onDelete: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = (item.depth * 20).dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Bullet(
                hasChildren = item.hasChildren,
                isCollapsed = item.isCollapsed,
                onClick = onToggle,
            )
            Spacer(Modifier.size(8.dp))
            Box(Modifier.weight(1f)) {
                if (editing) {
                    BasicTextField(
                        value = editBuffer,
                        onValueChange = { text ->
                            if (text.contains('\n')) {
                                onEditChange(text.replace("\n", ""))
                                onEnterCreatesSibling()
                            } else {
                                onEditChange(text)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { onEnterCreatesSibling() },
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                } else {
                    var menuOpen by remember { mutableStateOf(false) }
                    TitleRow(
                        node = item.node,
                        onClick = onBeginEdit,
                        onLongClick = { menuOpen = true },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("New sibling") },
                            onClick = {
                                menuOpen = false
                                onEnterCreatesSibling()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
        if (item.node.notes.isNotEmpty()) {
            Text(
                text = item.node.notes.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp, top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TitleRow(
    node: Node,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.todoState?.let { TodoChip(it) }
        node.priority?.let { PriorityChip(it) }
        Text(
            text = node.title.ifEmpty { "(untitled)" },
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (node.todoState == "DONE") TextDecoration.LineThrough else null,
                fontStyle = if (node.title.isEmpty()) FontStyle.Italic else null,
            ),
            color = if (node.title.isEmpty())
                MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        for (tag in node.tags) TagChip(tag)
    }
}

@Composable
private fun Bullet(hasChildren: Boolean, isCollapsed: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(24.dp)
            .clickable(enabled = hasChildren, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (hasChildren && isCollapsed) 14.dp else 8.dp)) {
            if (hasChildren && isCollapsed) {
                drawCircle(color = color, radius = size.minDimension / 2, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = color, radius = size.minDimension / 5)
            } else {
                drawCircle(color = color, radius = size.minDimension / 2)
            }
        }
    }
}

@Composable
private fun TodoChip(text: String) {
    val isDone = text == "DONE"
    val bg = if (isDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
    val fg = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = RoundedCornerShape(4.dp), color = bg) {
        Text(text, fontSize = 10.sp, color = fg, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun PriorityChip(p: Char) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "#$p",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TagChip(tag: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            tag,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ConflictDialog(
    onOverwrite: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File changed on disk") },
        text = { Text("This file was modified outside the app since you opened it. What would you like to do?") },
        confirmButton = {
            TextButton(onClick = onOverwrite) { Text("Overwrite") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("Discard mine") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
