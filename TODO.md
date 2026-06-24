# TODO — known rough edges and deferred polish

Things I've noticed during M3 and M4 that are not bugs but aren't worth fixing inline. Listed loosely by surface, not priority. Strike items as they're addressed; reference the commit that fixed them.

## ConflictDialog

- **Three buttons crammed into `dismissButton`.** Material 3's `AlertDialog` expects `confirmButton` + `dismissButton`. We need three actions (Overwrite / Discard mine / Cancel), so two of them share a `Row` inside `dismissButton`. Looks tight on narrow screens. Consider a `BottomSheetDialog` or a custom layout when polish budget allows.
- **"Discard mine" is ambiguous.** It currently reloads from disk (i.e. accepts the on-disk version, drops local edits). A user could read it as "delete the on-disk version and keep mine". Rename to "Reload from disk" or "Discard local edits".

## Editing

- **No "cancel edit" affordance.** System Back hides the keyboard but does not clear the edit buffer or exit edit mode. Tap-on-other-row commits; there's no explicit cancel. `cancelEdit()` exists in the ViewModel but is unwired in the UI.
- **`onCommitEdit` callback is dead.** `OutlineRow` accepts it but never invokes it. Either wire it to a focus-loss handler or remove the parameter.
- **Tapping the blank area below the LazyColumn does not commit.** Only tapping a sibling row or triggering a mutating op commits. Adds friction when only one row is in view.

## TreeOps perf

- **`findParentAndIndex` is O(N) per call.** Called multiple times per frame from `outdent`, `flatten` (indirectly via `findNode`), and the top-bar focused-title resolution. Fine under ~10k headings. If perf bites, add a `parentOf: Map<NodeId, NodeId>` derived once per tree mutation.
- **`OrgSerializer.serialize` runs on every Save.** O(file size). Fine for org files under ~1 MB; revisit if larger.

## ViewModel state

- **`OutlineState` has 14 fields.** Edges blur (e.g. `loading` vs `saving` vs `conflictPending`). Consider splitting into `FileState` (uri, fileName, root, originalText, dirty) + `UiState` (editing, editBuffer, collapsed, focusedRoot) + `IoState` (loading, saving, error, conflictPending).
- **`dirty` is set unconditionally** by `delete`, `indent`, `outdent`, `createSiblingAfter`, `appendInScope` — even when the operation could be detected as a no-op (e.g. deleting a freshly created empty row). Minor; the conflict dialog and save flow handle it correctly.
- **`nextNodeIdValue` overflow.** Long, so practically never overflows. No mitigation needed.

## Compose / UI

- **`DropdownMenu` inside `LazyColumn` row.** If the row scrolls out while the menu is open, the menu's `remember` state is lost. In practice the menu's scrim blocks scroll, so it doesn't bite, but technically fragile.
- **`zoomIn` blocks zoom on leaf nodes.** `node.children.isEmpty()` short-circuits. Acceptable today; if we ever build a "single-row notes editor" view, this guard will need to relax.
- **No animated zoom transition.** Going in/out of focus snaps. A `Crossfade` or `AnimatedContent` would soften it.
- **`flatten` recomputes from scratch on every collapse toggle.** O(N) per toggle. For a 10k-heading file, this is still ~ms. Skip-tree pruning would help but is premature.

## Parser correctness

- **Drawer / src-block boundaries are not validated on save.** Editing notes (M5) can break an open drawer or block. The serializer doesn't notice; on next open, the parser may absorb subsequent headings into the drawer. Acceptable footgun for now; consider a "validate before save" pass.
- **Lines inside notes that start with `*` become headings on reparse.** Notes editing introduces this risk; M5 should document it as a sharp edge.
- **`OrgParseConfig.todoKeywords` is fixed at `{TODO, DONE}`.** Org-mode supports custom workflows (e.g. `#+TODO: TODO WAITING | DONE CANCELLED`). Honour the `#+TODO:` header in a future milestone.

## Testing

- **No unit tests for `OutlineViewModel`.** AndroidViewModel ties it to the Application context; testing requires Robolectric or instrumentation. The structural logic could be extracted into a plain class with a small adapter for the Android side.
- **No on-device / instrumented tests.** All manual on-device verification today. Could add Compose UI tests for the critical flows (open, edit, save, conflict) when we have CI.

## Build / packaging

- **NixOS writable-SDK overlay** in `shell.nix` symlinks the read-only nix store into `~/.android/orgdroid-sdk`. Works but fragile if AGP ever wants to write to a path we haven't symlinked. Document this in the README when we have one.
- **`buildToolsVersion = "35.0.0"` is pinned** to prevent AGP from auto-installing 34. If we bump compile/targetSdk, remember to bump this too.
- **No F-Droid metadata yet.** No icon, no README, no metadata/ directory. Defer until we have something ship-worthy.
