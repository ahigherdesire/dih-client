# Storage Search

## Intent

Search items in containers the player has opened and highlight matching containers in the world. Display when the contents were last observed so remembered contents are not presented as live information.

## Existing implementation

- `baritone.cache.ChestMemory` persists container item IDs, names, counts, positions, dimensions, world/server scope, and timestamps in `baritone/chest_memory.json`.
- `#chest <item>` searches this memory. `#chest <N> goto`, `list`, `stats`, `forget`, and `clear` already exist.
- `MixinMinecraft.onSetScreen` records container contents when their screens close.
- StorageESP highlights storage geometry, but does not filter by remembered contents.
- Recording currently expires after eight seconds and ignores empty snapshots, which leaves stale search results after a container is emptied.

## Approach

Extend the existing memory and rendering facilities. A separate database would duplicate search, persistence, and world isolation and risk inconsistent results between the two commands. Preserve existing `#chest` syntax, with the approved change that selection highlights the chest instead of walking to it. Legacy `goto` syntax also selects a highlight.

## Proposed behavior

- Add `.storage search <item name or ID>` to search existing memory and activate matching-container outlines. Support multiword names.
- Report matching counts, coordinates, distance, and last-seen age. Identify results as remembered contents.
- Add `.storage clear` to clear the active highlights without deleting remembered containers.
- Highlight only matching containers in the current world and dimension. Bound the visible distance and marker count, and refresh results outside the render loop.
- Reuse depth-tested outlines. Keep results visible without needing to enable all of StorageESP.
- Improve capture so a legitimately open storage screen can remain open longer than eight seconds. Correctly refresh/remove records after emptying a container.
- Validate block/container association; do not record the player's inventory, unrelated server menus, or an ender chest as ordinary position-specific storage.
- Preserve existing saved data and keep reads client-side for multiplayer.

## Scope and validation

The first version uses a dot command and world highlights, without a new full-screen search UI. Nested shulker contents and mobile storage are outside this first version.

Test search normalization, scope/dimension filtering, empty updates, capture lifecycle, and result limits where logic can be separated from the client. Build and run tests for `:26.3-fabric` using the specified JDK. Commit locally; publishing a new release is separate work.
