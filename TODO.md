# TODO — known rough edges and deferred polish

Things I've noticed during M3–M9 that are not bugs but aren't worth fixing inline. Listed loosely by surface, not priority. Strike items as they're addressed; reference the commit that fixed them.

## Deferred features (not bugs — missing capability)

- ~~Move up / Move down siblings.~~ Shipped in M6 (15ac622).
- ~~Recent files / multi-file sidebar.~~ Shipped in M7 (4952a16) with auto-open + back-to-recents + dirty-confirm.
- ~~Priority editing (`[#A]/[#B]/[#C]`).~~ Shipped in M9 (423bb78) — metadata sheet radio.
- ~~Tag editing (`:tag:`).~~ Shipped in M9 (423bb78) — metadata sheet chips + input.
- ~~Search / filter.~~ Shipped in M8 (f12626c) — filter mode, ancestors of matches surfaced.
- **Search by tag.** M8 only matches title + notes; M9 added tag editing but didn't extend the search haystack. A heading whose only "urgent" hit is `:urgent:` still won't match. Add a `tag:` prefix or always-include-tags.
- **Custom `#+TODO:` keyword workflows.** Hardcoded `null → TODO → DONE → null` cycle. See Parser correctness.
- **Live notes formatting** (links, bold, lists). Monospace plain text only.
- **Undo / redo.** No safety net for destructive ops.

## ConflictDialog

- **Three buttons crammed into `dismissButton`.** Material 3's `AlertDialog` expects `confirmButton` + `dismissButton`. We need three actions (Overwrite / Discard mine / Cancel), so two of them share a `Row` inside `dismissButton`. Looks tight on narrow screens. Consider a `BottomSheetDialog` or a custom layout when polish budget allows.
- **"Discard mine" is ambiguous.** It currently reloads from disk (i.e. accepts the on-disk version, drops local edits). A user could read it as "delete the on-disk version and keep mine". Rename to "Reload from disk" or "Discard local edits".

## Editing

- **No "cancel edit" affordance** for title OR notes. System Back hides the keyboard but does not clear the edit buffer or exit edit mode. Tap-on-other-row commits; there's no explicit cancel. `cancelEdit()` and `cancelNotes()` exist in the ViewModel but are unwired in the UI.
- **`onCommitEdit` and `onCommitNotes` callbacks are dead.** `OutlineRow` accepts both but never invokes them — there is no focus-loss listener. Either wire them up or drop the parameters.
- **Tapping the blank area below the LazyColumn does not commit.** Applies to both title and notes edits. Only tapping a sibling row or triggering a mutating op commits. Adds friction when only one row is in view.
- **Notes editor swallows the bullet's "single-line" feel.** Tapping a row's notes block expands the row vertically; nothing visually signals "edit mode" beyond the cursor. Consider a faint background tint while editing.

## Search (M8)

- **Tags are not searched.** Only `node.title` and `node.notes` are indexed. A heading whose match is in its `:tag:` block stays hidden. Either always include tags in the haystack or add a `tag:` prefix syntax.
- **Synthetic root header notes are not searched** (file header before first heading). Intentional — searching them would force "everything visible" — but worth noting.
- **No debounce.** Every keystroke recomputes `Search.visibleIds`. Fine under ~20k headings; a `LaunchedEffect(query) { delay(50) }` shim would help on huge files.
- **Search field placeholder is a sibling `Text` over `BasicTextField`.** Works but not Material's intended pattern. Move to `TextField` with a real placeholder if we ever stop avoiding `OutlinedTextField`'s height.
- **No match counter / "n of m" navigation.** Filter mode by design; deferred.
- **No matched-substring highlight.** Plain `Text`. `AnnotatedString` styling deferred.
- **Collapse-toggle during search is invisible.** Tapping a bullet while filtering does mutate `collapsed` but `flatten` ignores it during filter mode; the change only surfaces after the search closes. Acceptable footgun.
- **`SearchBarRow` re-focuses on every recomposition of its parent if it's keyed wrong.** Currently `LaunchedEffect(Unit)` guards it; don't accidentally make it `LaunchedEffect(query)` during refactors.

## Metadata sheet (M9)

- **Tap-on-chip still opens title edit**, not the sheet. The `combinedClickable` on `TitleRow` consumes the gesture before any child chip can claim it. Intentional for M9 — wrapping each chip in its own `clickable` is fragile mid-editing. Revisit during a "chip overhaul" milestone.
- **Priority picker only exposes A/B/C/None.** Parser still accepts `[#D]`–`[#Z]`; values outside A-C survive a sheet open + close untouched (no radio selected), but the user can't author them from the UI. Acceptable for typical org workflows.
- **Tag input is case-sensitive in dedupe.** `Work` and `work` coexist. Matches Emacs org-mode semantics. Mention in a future README.
- **`IconButton(size = 24.dp)` on the × button** doesn't actually shrink the ripple — M3 enforces 40dp min touch target. Cosmetic; replace with `Box.clickable` if we want a tighter hit area.
- **Sheet sits as a peer of dialogs.** If `conflictPending` becomes true while the sheet is open (e.g. user taps Save → conflict), both render. The sheet doesn't auto-dismiss. Acceptable; both surfaces are functional.
- **No "Edit metadata" affordance from the empty/recents screen.** Sheet is per-heading; only reachable from a row's long-press. Correct by design.

## Recents (M7)

- **Failed auto-open leaves the dead entry in place.** If a recent URI was revoked or the file deleted, opening it sets `state.error` but the entry stays in the list. User must long-press → Remove. Could auto-evict on `SecurityException` or `FileNotFoundException`; deferred to keep failure semantics simple.
- **No "Clear all recents" bulk action.** Single-tap removal only.
- **No timestamp or path preview** in the recents row. Two files named `notes.org` from different folders are indistinguishable.
- **`RecentFilesStore` has no unit tests.** The codec is fully tested; the store wraps SharedPreferences and would need Robolectric. Acceptable thinness given the store has ~10 lines per method.
- **No confirmation on remove.** One tap from a menu and the entry is gone. The user can always reopen via SAF; not a data-loss path.
- **`Uri.parse` of stored URI strings is trusted.** A corrupted SharedPreferences value with garbage URI text could blow up `open()`. Wrapped in try-catch in `init`, but `vm.open(Uri.parse(item.uri))` from `RecentRow` could surface a non-actionable error. Low risk; SharedPreferences corruption is rare.

## TreeOps perf

- **`findParentAndIndex` is O(N) per call.** Called multiple times per frame from `outdent`, `flatten` (indirectly via `findNode`), and the top-bar focused-title resolution. Fine under ~10k headings. If perf bites, add a `parentOf: Map<NodeId, NodeId>` derived once per tree mutation.
- **`OrgSerializer.serialize` runs on every Save.** O(file size). Fine for org files under ~1 MB; revisit if larger.

## ViewModel state

- **`OutlineState` has 21 fields** after M9 added `metadataSheetFor`. Edges blur (e.g. `loading` vs `saving` vs `conflictPending` vs `closePending`). Consider splitting into `FileState` (uri, fileName, root, originalText, dirty) + `UiState` (editing, editBuffer, editingNotes, notesBuffer, collapsed, focusedRoot, searchActive, searchQuery, metadataSheetFor) + `IoState` (loading, saving, error, conflictPending, closePending) + `LibraryState` (recents). The refactor is overdue but blocks none of the next milestones.
- **`discardLocal` only resets a subset of UI state** (editing/notesBuffer/collapsed) but leaves `focusedRoot`, `searchActive`, `searchQuery`, and `metadataSheetFor` dangling at NodeIds from the discarded tree. The sheet auto-hides (findNode returns null) and search re-derives against the new tree; focus-resolution silently degrades. Add a "wipe transient UI state" helper used by both `open()` and `discardLocal()`.
- **`commitEditInternal` name is now misleading.** It commits BOTH title and notes since M5. Rename to `commitPending` (or `flushBuffers`) when next touched. Spec kept the old name to minimize diff churn.
- **`dirty` is set unconditionally** by `delete`, `indent`, `outdent`, `createSiblingAfter`, `appendInScope`, `cycleTodo`, `moveUp`, `moveDown` — even when the operation could be detected as a no-op (e.g. deleting a freshly created empty row). Minor; the conflict dialog and save flow handle it correctly. (M9's `setPriority`/`addTag`/`removeTag` deliberately *do* guard on no-op to avoid spurious dirty flips when re-selecting the same radio.)
- **`nextNodeIdValue` overflow.** Long, so practically never overflows. No mitigation needed.
- **`init {}` reads SharedPreferences synchronously** before any UI subscriber. Fast (~µs for a single string read of 10 entries) but technically blocks the main thread during ViewModel construction. If recents storage grows or becomes structured, async this via `viewModelScope`.

## Compose / UI

- **`DropdownMenu` inside `LazyColumn` row.** If the row scrolls out while the menu is open, the menu's `remember` state is lost. In practice the menu's scrim blocks scroll, so it doesn't bite, but technically fragile.
- **Dropdown menu has 10 items** after M9: Indent, Outdent, Move up, Move down, New sibling, Toggle TODO, Add/Edit notes, Edit metadata, Zoom in, Delete. Consider grouping (Move ▸, Edit ▸) or a bottom-sheet variant.
- **`OutlineRow` has 22 parameters** after M9 added `onEditMetadata`. Bundling the callbacks into a `OutlineRowCallbacks` data class would make changes cheaper. Defer until next signature change.
- **`zoomIn` blocks zoom on leaf nodes.** `node.children.isEmpty()` short-circuits. Acceptable today; if we ever build a "single-row notes editor" view, this guard will need to relax.
- **No animated zoom transition.** Going in/out of focus snaps. A `Crossfade` or `AnimatedContent` would soften it.
- **`flatten` recomputes from scratch on every collapse toggle.** O(N) per toggle. For a 10k-heading file, this is still ~ms. Skip-tree pruning would help but is premature.

## Parser correctness

- **Drawer / src-block boundaries are not validated on save.** Notes editing (shipped in M5) can break an open drawer or block. The serializer doesn't notice; on next open, the parser may absorb subsequent headings into the drawer. Acceptable footgun for now; consider a "validate before save" pass that flags suspicious notes content (unterminated `:DRAWER:`, removed `:END:`, removed `#+END_SRC`).
- **Lines inside notes that start with `*` become headings on reparse.** Notes editing introduces this risk. On the next open, a user-typed `* fake heading` line splits off into its own node, taking subsequent body lines with it. Validate or escape on save.
- **`OrgParseConfig.todoKeywords` is fixed at `{TODO, DONE}`.** Org-mode supports custom workflows (e.g. `#+TODO: TODO WAITING | DONE CANCELLED`). Honour the `#+TODO:` header in a future milestone. M5's `cycleTodoState` defensively returns `null` for unknown keywords so users at least won't get stuck.

## Testing

- **No unit tests for `OutlineViewModel`.** AndroidViewModel ties it to the Application context; testing requires Robolectric or instrumentation. The structural logic could be extracted into a plain class with a small adapter for the Android side.
- **No on-device / instrumented tests.** All manual on-device verification today. Could add Compose UI tests for the critical flows (open, edit, save, conflict) when we have CI.

## Build / packaging

- **NixOS writable-SDK overlay** in `shell.nix` symlinks the read-only nix store into `~/.android/orgdroid-sdk`. Works but fragile if AGP ever wants to write to a path we haven't symlinked. Document this in the README when we have one.
- **`buildToolsVersion = "35.0.0"` is pinned** to prevent AGP from auto-installing 34. If we bump compile/targetSdk, remember to bump this too.
- **No F-Droid metadata yet.** No icon, no README, no metadata/ directory. Defer until we have something ship-worthy.
