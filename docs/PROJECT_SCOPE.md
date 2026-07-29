# Project scope

ForceBlend 1.0.0 is a focused proof of concept maintained for the community. Its job is to prepare the existing side of
an Overworld border so Minecraft can apply native terrain blending when the missing side is generated.

## In scope

- One Fabric JAR for stable Minecraft 1.18-1.21.11 and 26.1-26.2.
- Local singleplayer worlds.
- Existing Overworld terrain stored in vanilla Anvil region files.
- Legacy root and modern nested Overworld paths.
- GZIP, ZLIB, uncompressed, LZ4, and external `.mcc` chunk streams.
- The 1.18 `old_noise` marker and the 1.19+ section-range marker.
- Complete chunks with normalized status `minecraft:full`.
- A seven-chunk frontier around missing or incomplete terrain.
- Outer borders, deleted cavities, and imported terrain islands.
- Safe removal of old ForceBlend markers from incomplete chunks.
- Transactional region replacement, coordinate checks, and repeatable no-op scans.

## Out of scope

ForceBlend does not:

- process the Nether, the End, custom dimensions, multiplayer, or dedicated servers;
- edit entities, POI data, structures, blocks, the seed, or generator settings;
- select, delete, move, or regenerate chunks;
- repair corrupt worlds or already-generated hard borders;
- support CubicChunks or another non-vanilla chunk storage model;
- guarantee clean structure placement where old and new terrain meet;
- promise snapshots, future Minecraft versions, ongoing maintenance, or fast support.

## Chunk rules

The scanner validates a chunk's Anvil location, stream length, compression, NBT root, coordinates, `DataVersion`, and
status before any region is replaced.

- Chunks older than DataVersion 2844 are left for Minecraft's native migration.
- Only complete `full` chunks can become blending sources.
- The marker format comes from the running Minecraft version, not from the chunk's `DataVersion`.
- ForceBlend never changes `DataVersion` or promotes a chunk's status.
- An eligible chunk is marked only if missing or incomplete terrain exists within seven chunks on either axis.
- Deep-interior chunks remain untouched.
- Unsafe markers on incomplete chunks are removed independently of frontier selection.
- `Heightmaps` and `isLightOn` are removed only when an eligible marker is created or replaced.

Minecraft 1.18-1.18.2 receives:

```snbt
blending_data: { old_noise: 1b }
```

Minecraft 1.19 and newer receives:

```snbt
blending_data: {
    min_section: <minimum section, at most -4>,
    max_section: <maximum section, at least 20>
}
```

## Safety promises inside the implementation

- Never follow writable region or external-chunk symbolic links.
- Never replace a region until every discovered chunk in it has passed validation.
- Never replace an unchanged region.
- Stop world loading when processing cannot finish safely.
- Never modify the source passed to the real-world copy test.
- Enable exactly one lifecycle adapter for the running game.
- Recompute topology on every open instead of trusting remembered seed or state information.

These safeguards reduce risk but do not replace a backup. Power loss, filesystem failure, third-party editors, and
unknown mod interactions remain outside the program's control.

## Release policy

Version 1.0.0 is the first stable community release. Only Minecraft 1.18.2 and 26.2 are currently verified in game.
Other declared versions remain mapping-audited until the community supplies reproducible runtime results.

The author may not publish future releases. Anyone may fork, maintain, or adapt the project under the MIT License, and
the mod may be redistributed in modpacks without prior permission.
