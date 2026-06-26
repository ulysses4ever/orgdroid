# orgdroid — a simple reader/editor for Org files on Android

A Workflowy-style hierarchical outline editor that reads and writes standard
Emacs `.org` files. Open any `.org` file via the system file picker; edit
headings, notes, TODO states, priorities, and tags; save back in place.

## Building

### Prerequisites

- **NixOS / Nix** (recommended) — the `shell.nix` pins the exact Android SDK,
  Gradle, Kotlin, and JDK versions and sets up a writable SDK overlay
  automatically.
- **Non-Nix** — you need JDK 17, Gradle 8.10.2, and Android SDK with
  platform 35 and build-tools 35.0.0 installed. Set `ANDROID_HOME` and
  ensure `local.properties` contains `sdk.dir=<path>`.

### With Nix (recommended)

```sh
nix-shell          # sets up SDK overlay, exports ANDROID_HOME, writes local.properties
./gradlew test     # run unit tests
./gradlew assembleDebug   # build APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # build and install on a connected device / emulator
```

The `shellHook` in `shell.nix` creates a writable symlink overlay of the
read-only Nix store SDK at `~/.android/orgdroid-sdk` so AGP can write install
metadata. `GRADLE_OPTS` overrides `aapt2` to the Nix-provided binary (the
Maven-downloaded one won't run on NixOS due to ELF interpreter mismatch).

### Without Nix

```sh
export ANDROID_HOME=/path/to/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew test
./gradlew assembleDebug
```

## Project layout

```
app/src/main/kotlin/dev/orgdroid/
  org/          OrgParser, OrgSerializer, Node, TreeOps, Search
  outline/      OutlineViewModel, OutlineState, Undo
  file/         FileIo (SAF read/write)
  recents/      RecentFilesStore (SharedPreferences)
  ui/           OutlineScreen (all Compose UI)
app/src/test/kotlin/dev/orgdroid/
  org/          OrgParserTest, OrgSerializerTest, TreeOpsTest, SearchTest
  outline/      UndoTest
```

## Status

M1–M10 shipped. See `TODO.md` for known rough edges and deferred polish.

## License

Licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE)
for the full text.
