# Storage Search Implementation Plan

> **For agentic workers:** Use executing-plans to implement this plan task by task.

**Goal:** Search remembered container contents and highlight matching chests without walking.

**Architecture:** Keep ChestMemory as the persistent database and reuse the region renderer's depth-tested box outlines. Add a dot command and bounded, periodically refreshed highlight state. Fix capture lifetime and empty snapshots in the shared database.

**Tech Stack:** Java 25, Minecraft 26.3, Fabric, Brigadier, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-11-20-storage-search-design.md`, approved with the clarification that selecting a chest highlights it instead of walking.

## Global Constraints

- Client-side only; reuse the existing saved data and world/server scope.
- `.storage search <item>` highlights; `.storage clear` clears highlights without deleting memory.
- No automatic movement. Existing `#chest` searches share highlights; legacy goto syntax selects a highlight.
- Bound highlights to 64 containers within 128 blocks; refresh at most once per 10 ticks.
- Keep unrelated changes and the private repository untouched. Commit locally; no release or push for this task.

## Review Focus

- Container kept open longer than eight seconds must still update memory.
- Empty snapshot must remove prior items, including either half of a double chest.
- Inventory, ender chests, mismatched server menus and unsynced screens must not overwrite physical storage.
- A changed world, dimension or menu must invalidate capture and visible highlights.
- Query normalization, nearest-result ordering, distance and marker limits must hold independently of rendering.

### Task 1: Correct shared memory and capture

**Files:** `baritone/cache/ChestMemory.java`, `src/launch/java/baritone/launch/mixins/MixinMinecraft.java`, `dihclient/mixin/DihGuiSetScreenMixin.java`, `dihclient/mixin/DihClientPlayNetworkHandlerMixin.java`, `dihclient/mixin/DihMultiPlayerGameModeMixin.java`, `src/test/java/baritone/cache/ChestMemoryTest.java`, `src/test/java/dihclient/mixin/StorageCaptureHookTest.java`.

- [x] Write failing tests for empty replacement, scoped search, normalization, capture identity/lifetime, supported block/menu pairs and double-chest updates.
- [x] Run focused tests and observe missing behavior.
- [x] Extract a small in-memory store within ChestMemory; preserve the JSON schema. Bind capture to level/menu/world/dimension, validate physical storage, require full container sync, and remove the wall-clock expiry. Update the interaction/screen/sync hooks.
- [x] Run focused tests to green.

### Task 2: Command and highlights

**Files:** `dihclient/commands/impl/StorageCommand.java`, `dihclient/commands/DihCommands.java`, `dihclient/util/DihStorageSearch.java`, `dihclient/modules/StorageSearchRenderer.java`, `baritone/command/defaults/ChestCommand.java`, associated JUnit tests.

- [x] Write failing selection tests covering scope, dimension, nearest-first ordering, caps, clear, world changes and timestamps, plus command parsing tests.
- [x] Run focused tests and observe missing behavior.
- [x] Implement the command, periodic refresh and depth-tested outlines using `RegionHighlightRenderer.boxEdges`. Wire legacy search and selection to highlights instead of pathing.
- [x] Run focused tests to green.

### Task 3: Verify and finish

- [x] Run `:26.3-fabric:build` including the full test suite with the specified JDK.
- [x] Review changes for capture attribution, stale results, world isolation and absence of pathing.
- [x] Document usage, commit the implementation, and report verification and runtime limits.

## Verification ledger

- Memory tests first failed on missing Store/Capture/storage validation, then passed (6 tests).
- Search and command tests first failed on missing command/selection behavior, then passed (7 tests).
- Actual-interaction attribution test first failed on missing Interaction, then passed in the full suite.
- Explicit result selection test first failed on missing focused selection, then passed in the full suite.
- Fresh review found the pre-existing Minecraft.setScreenAndShow hook is bypassed by ordinary container screens. Moved capture to the existing Gui.setScreen mixin, removed the obsolete hook, and added a vanilla-bytecode call-path regression test. It failed on the old hook and passes with the fix.
- World transitions now cancel capture and pending interaction; the pending interaction uses a weak level reference.
- Final `:26.3-fabric:build`: BUILD SUCCESSFUL, 2,103 tests, zero failures/errors, one skipped. Fifteen new tests passed.
- Build and RED/GREEN logs are retained under ignored `build/storage-search-verification/`.
- Runtime scope: the Java build and unit tests validate lifecycle/selection logic; in-game rendering has not been exercised in this task.
