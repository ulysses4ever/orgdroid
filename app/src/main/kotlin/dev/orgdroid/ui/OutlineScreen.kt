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
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import dev.orgdroid.org.TreeOps
import dev.orgdroid.outline.OutlineViewModel
import dev.orgdroid.recents.RecentFile

private data class RenderItem(
    val node: Node,
    val depth: Int,
    val hasChildren: Boolean,
    val isCollapsed: Boolean,
    val canIndent: Boolean,
    val canOutdent: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

private fun flatten(
    root: Node?,
    collapsed: Set<NodeId>,
    focusedRoot: NodeId?,
): List<RenderItem> {
    if (root == null) return emptyList()
    val startNode = if (focusedRoot != null) TreeOps.findNode(root, focusedRoot) else root
    if (startNode == null) return emptyList()

    val out = mutableListOf<RenderItem>()
    fun visit(parent: Node, node: Node, indexInParent: Int, depth: Int) {
        val isCollapsed = node.id in collapsed
        if (node.id != startNode.id) {
            out.add(
                RenderItem(
                    node = node,
                    depth = depth,
                    hasChildren = node.children.isNotEmpty(),
                    isCollapsed = isCollapsed,
                    canIndent = indexInParent > 0,
                    canOutdent = parent.id != startNode.id,
                    canMoveUp = indexInParent > 0,
                    canMoveDown = indexInParent < parent.children.size - 1,
                )
            )
        }
        if (!isCollapsed) {
            val childDepth = if (node.id == startNode.id) 0 else depth + 1
            for ((i, c) in node.children.withIndex()) visit(node, c, i, childDepth)
        }
    }
    visit(startNode, startNode, -1, 0)
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(vm: OutlineViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.open(uri) }

    val items by remember(state.root, state.collapsed, state.focusedRoot) {
        derivedStateOf { flatten(state.root, state.collapsed, state.focusedRoot) }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(state.editing) {
        val id = state.editing ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.node.id == id }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }
    LaunchedEffect(state.editingNotes) {
        val id = state.editingNotes ?: return@LaunchedEffect
        val idx = items.indexOfFirst { it.node.id == id }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = state.fileName ?: "orgdroid"
                    val focusedTitle = state.focusedRoot?.let { id ->
                        state.root?.let { TreeOps.findNode(it, id)?.title }
                    }
                    val display = when {
                        focusedTitle == null -> name
                        focusedTitle.isEmpty() -> "(untitled)"
                        else -> focusedTitle
                    }
                    Text(if (state.dirty && state.focusedRoot == null) "$display •" else display)
                },
                navigationIcon = {
                    if (state.focusedRoot != null) {
                        IconButton(onClick = { vm.zoomOut() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zoom out")
                        }
                    }
                },
                actions = {
                    if (state.root != null || state.uri != null) {
                        IconButton(onClick = { vm.closeFile() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close file")
                        }
                    }
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
                FloatingActionButton(onClick = { vm.appendInScope() }) {
                    Icon(Icons.Filled.Add, contentDescription = "New heading")
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(16.dp))
                state.root == null -> EmptyState(
                    recents = state.recents,
                    error = state.error,
                    onOpen = { uri -> vm.open(uri) },
                    onRemove = { uri -> vm.removeRecent(uri) },
                )
                state.error != null -> Text(
                    "Error: ${state.error}",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                    items(items, key = { it.node.id.value }) { item ->
                        OutlineRow(
                            item = item,
                            editing = state.editing == item.node.id,
                            editBuffer = state.editBuffer,
                            editingNotes = state.editingNotes == item.node.id,
                            notesBuffer = state.notesBuffer,
                            onToggle = { vm.toggleCollapse(item.node.id) },
                            onBeginEdit = { vm.beginEdit(item.node.id) },
                            onEditChange = vm::onEditBufferChange,
                            onCommitEdit = { vm.commitEdit() },
                            onEnterCreatesSibling = { vm.createSiblingAfter(item.node.id) },
                            onDelete = { vm.delete(item.node.id) },
                            onIndent = { vm.indent(item.node.id) },
                            onOutdent = { vm.outdent(item.node.id) },
                            onZoomIn = { vm.zoomIn(item.node.id) },
                            onCycleTodo = { vm.cycleTodo(item.node.id) },
                            onBeginEditNotes = { vm.beginEditNotes(item.node.id) },
                            onNotesChange = vm::onNotesBufferChange,
                            onCommitNotes = { vm.commitNotes() },
                            onMoveUp = { vm.moveUp(item.node.id) },
                            onMoveDown = { vm.moveDown(item.node.id) },
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

    if (state.closePending) {
        CloseConfirmDialog(
            onDiscard = { vm.confirmCloseDiscard() },
            onCancel = { vm.cancelClose() },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OutlineRow(
    item: RenderItem,
    editing: Boolean,
    editBuffer: String,
    editingNotes: Boolean,
    notesBuffer: String,
    onToggle: () -> Unit,
    onBeginEdit: () -> Unit,
    onEditChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onEnterCreatesSibling: () -> Unit,
    onDelete: () -> Unit,
    onIndent: () -> Unit,
    onOutdent: () -> Unit,
    onZoomIn: () -> Unit,
    onCycleTodo: () -> Unit,
    onBeginEditNotes: () -> Unit,
    onNotesChange: (String) -> Unit,
    onCommitNotes: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val titleFocusRequester = remember { FocusRequester() }
    val notesFocusRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) titleFocusRequester.requestFocus()
    }
    LaunchedEffect(editingNotes) {
        if (editingNotes) notesFocusRequester.requestFocus()
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
                            .focusRequester(titleFocusRequester),
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
                        onCycleTodo = onCycleTodo,
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Indent") },
                            enabled = item.canIndent,
                            onClick = { menuOpen = false; onIndent() },
                        )
                        DropdownMenuItem(
                            text = { Text("Outdent") },
                            enabled = item.canOutdent,
                            onClick = { menuOpen = false; onOutdent() },
                        )
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            enabled = item.canMoveUp,
                            onClick = { menuOpen = false; onMoveUp() },
                        )
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            enabled = item.canMoveDown,
                            onClick = { menuOpen = false; onMoveDown() },
                        )
                        DropdownMenuItem(
                            text = { Text("New sibling") },
                            onClick = { menuOpen = false; onEnterCreatesSibling() },
                        )
                        DropdownMenuItem(
                            text = { Text("Toggle TODO") },
                            onClick = { menuOpen = false; onCycleTodo() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.node.notes.isEmpty()) "Add notes" else "Edit notes") },
                            onClick = { menuOpen = false; onBeginEditNotes() },
                        )
                        DropdownMenuItem(
                            text = { Text("Zoom in") },
                            enabled = item.hasChildren,
                            onClick = { menuOpen = false; onZoomIn() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
        if (editingNotes) {
            BasicTextField(
                value = notesBuffer,
                onValueChange = onNotesChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 32.dp, top = 2.dp)
                    .focusRequester(notesFocusRequester),
                singleLine = false,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        } else if (item.node.notes.isNotEmpty()) {
            Text(
                text = item.node.notes.joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 32.dp, top = 2.dp)
                    .clickable { onBeginEditNotes() },
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
    onCycleTodo: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        node.todoState?.let { TodoChip(it, onCycleTodo) }
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
private fun TodoChip(text: String, onClick: () -> Unit) {
    val isDone = text == "DONE"
    val bg = if (isDone) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
    val fg = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
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
private fun EmptyState(
    recents: List<RecentFile>,
    error: String?,
    onOpen: (Uri) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (error != null) {
            Text(
                "Error: $error",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
        }
        if (recents.isEmpty()) {
            Text("No file open. Tap Open.", Modifier.padding(16.dp))
        } else {
            Text(
                "Recent files",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(recents, key = { it.uri }) { item ->
                    RecentRow(
                        item = item,
                        onOpen = { onOpen(Uri.parse(item.uri)) },
                        onRemove = { onRemove(item.uri) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    item: RecentFile,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Remove from recents") },
                onClick = { menuOpen = false; onRemove() },
            )
        }
    }
}

@Composable
private fun CloseConfirmDialog(
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Discard unsaved changes?") },
        text = { Text("You have unsaved edits. Closing this file will discard them.") },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text("Discard") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
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
