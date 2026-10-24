# Project scope

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

ForceBlend prepares the existing side of an Overworld border for Minecraft's native terrain blending.
Version 1.2.0 integrates the 1.1.0 workflow with the 26.3, dedicated-server, and NeoForge ports.

## Supported boundaries

- Fabric targets stable Minecraft 1.18-1.21.11 and 26.1-26.3 in one Fabric artifact.
- NeoForge has a separate artifact for Minecraft 26.3 and NeoForge 26.3.0.90 or newer in that family.
- Singleplayer and dedicated servers use the same analysis and application core.
- Storage is vanilla Overworld Anvil, including root and nested Overworld layouts.
- Compression support remains GZIP, ZLIB, uncompressed, LZ4, and external `.mcc` streams.
- Only complete chunks near missing or incomplete terrain become blending sources.
- Nether, End, custom dimensions, entities, POIs, and structure data are not processed.
- Terrain generation, chunk deletion/import, seed editing, retroactive blending, and CubicChunks remain outside the scope.
- Forge, snapshots, and unreviewed future versions are not supported by this integration.

## Execution contract

The mod ID is `forceblend`; the display name is ForceBlend. The development branch is `1.2.0`, starting from the
`1.1.0` beta.
Run mode defaults to `once`, with an `every-open` option.

In once mode, `forceblend.json` or the legacy `forceblendonce.json` skips the world before analysis. Completion is recorded
only after a successful application with existing chunks and no deferred work. Failures and chunk limits must not cause
unfinished work to be marked complete. Every-opening mode ignores completion markers.

Clients retain read-only analysis and the four choices: apply, back up and apply, enter unchanged, or cancel.
No backup or world write occurs while a client is waiting for that decision. Automatic analysis can be disabled.
Native Minecraft screens expose real work counts, progress, ETA, active regions, and a completion message. Analysis and
application do not block the render thread. No desktop UI is required, and a failed UI initialization cancels opening.

Dedicated servers have no dialogs. With automatic processing enabled, they analyze, complete a world backup, validate,
apply, and log. Backup failure stops the run before terrain writes. Disabling automatic processing skips that server
run. Backups always use `<game>/backups/forceblend/<world>/`.

## Data rules

The scanner validates Anvil entries, compression, NBT roots, coordinates, `DataVersion`, and chunk status.
Pre-1.18 data remains the responsibility of Minecraft's native migration. Only `full` chunks within the seven-chunk
frontier can receive a blending marker. Unsafe legacy markers on incomplete chunks can be removed independently.

Minecraft 1.18-1.18.2 uses `blending_data: {old_noise: 1b}`; 1.19+ uses section bounds.
The source `DataVersion` and status are preserved. Height/light caches are invalidated only for newly prepared chunks.
No region without selected changes is replaced.

Parallel selection remains deterministic, with one writer per region. Stale plans are rejected before writes.
Region replacement and external-stream rollback are per-region safeguards, not an all-world transaction.

Backups preserve world data except the runtime root `session.lock`. ZIPs are published only when complete, never
overwrite an older backup, reject unsafe paths and links, and are discarded if source metadata changes.

## Release boundary

The target is 1.2.0, distributed through CurseForge from Mauro0805/ForceBlend. Both maintainers are credited in loader
metadata. The existing MIT license and original copyright are retained.

Merged-build gameplay checks remain required. Results from stable 1.0.0 and the separate ForceBlend Once builds are
historical evidence, not a test certificate for 1.2.0. No update schedule or performance speedup is promised.

## Source layout and future Paper work

`common/` contains shared sources; `fabric/` and `neoforge/` contain separate loader adapters and builds.
The root build coordinates both artifacts. `paper/README.md` reserves a future-plugin checklist only.
Paper implementation, dependencies, runtime support, and publication are outside the 1.2.0 release.
