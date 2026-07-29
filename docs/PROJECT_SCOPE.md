# Project scope

ForceBlend is a focused proof of concept maintained for the community. Its job is to prepare the existing side of an
Overworld border so Minecraft can apply native terrain blending when the missing side is generated.

Version 1.0.0 is the stable reference release. Version 1.1.0-beta.1 improves the workflow around the same core job; it
does not turn ForceBlend into a terrain editor or generator.

## In scope

- One Fabric JAR for stable Minecraft 1.18-1.21.11 and 26.1-26.2.
- Local singleplayer worlds and existing Overworld terrain.
- Legacy root and modern nested Overworld paths using vanilla Anvil storage.
- GZIP, ZLIB, uncompressed, LZ4, and external `.mcc` chunk streams.
- The 1.18 `old_noise` marker and the 1.19+ section-range marker.
- Complete chunks with normalized status `minecraft:full`.
- A seven-chunk frontier around missing or incomplete terrain.
- Outer borders, deleted cavities, and imported terrain islands.
- Safe removal of old ForceBlend markers from incomplete chunks.
- A read-only pre-analysis with exact work counts and an approximate duration.
- Explicit choices to apply, apply with a backup, enter unchanged, or cancel.
- Optional complete world backups before modification.
- Deterministic per-opening chunk limits and continuation on later opens.
- Parallel region work with progress, ETA, active regions, and completion notification.
- Optional Mod Menu settings and a standalone configuration file.
- Stale-plan validation and transactional region replacement.

## Out of scope

ForceBlend does not:

- process the Nether, the End, custom dimensions, multiplayer, or dedicated servers;
- edit entities, POI data, structures, blocks, the seed, or generator settings;
- select, delete, move, or regenerate chunks;
- repair corrupt worlds or blend a border after both sides have already generated;
- support CubicChunks or another non-vanilla chunk storage model;
- guarantee clean structure placement where old and new terrain meet;
- promise snapshots, future Minecraft versions, ongoing maintenance, or fast support;
- promise a particular speedup from parallel processing.

## World-open contract

Automatic analysis is on by default. The analysis may read and decompress chunk data, but it must not alter the world.
Its result is kept as an immutable in-memory plan and used by the confirmation dialog.

The four post-analysis choices have strict meanings:

- **Apply and enter:** validate the plan, process its selected chunks, then continue opening.
- **Back up, apply and enter:** complete a world ZIP first, then validate and apply.
- **Enter unchanged:** discard the plan and open without modification.
- **Cancel:** discard the plan and stop this world-open attempt.

When automatic analysis is disabled, ForceBlend asks whether to analyze, enter unchanged, or cancel before scanning.
When automatic backup is enabled, every apply action must complete a backup first.

## Chunk rules

The scanner validates a chunk's Anvil location, stream length, compression, NBT root, coordinates, `DataVersion`, and
status before a plan is eligible for application.

- Chunks older than DataVersion 2844 are left for Minecraft's native migration.
- Only complete `full` chunks can become blending sources.
- The marker format comes from the running Minecraft version, not from the chunk's `DataVersion`.
- ForceBlend never changes `DataVersion` or promotes a chunk's status.
- A complete chunk is eligible only if missing or incomplete terrain exists within seven chunks on either axis.
- Deep-interior chunks remain untouched.
- Unsafe markers on incomplete chunks are removed independently of frontier selection.
- `Heightmaps` and `isLightOn` are removed only when an eligible marker is created or replaced.
- A configured limit chooses a deterministic prefix of eligible work; the remainder is reported as deferred.

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

## Safety invariants

- Never write during pre-analysis or while waiting for a user choice.
- Never follow writable region, external-chunk, backup-source, or backup-destination symbolic links.
- Never apply an analysis after its region set or captured snapshots become stale.
- Never let two workers write the same region file.
- Never replace an unchanged region.
- Never publish an incomplete backup or silently overwrite an older one.
- Stop world loading when analysis, backup, validation, or application cannot finish safely.
- Enable exactly one lifecycle adapter for the running game.
- Recompute topology on every requested analysis instead of trusting remembered seed or state information.

These safeguards do not replace an offline backup. Power loss, filesystem failure, third-party editors, and unknown mod
interactions remain outside the program's control.

## Release and community policy

Stable 1.0.0 is verified in game on Minecraft 1.18.2 and 26.2. The 1.1.0-beta.1 automated suite passes, but the beta
still awaits fresh in-game tests on those versions. Other declared versions remain mapping-audited until reproducible
runtime results are supplied.

The author may not publish future releases. Anyone may fork, maintain, or adapt the project under the MIT License, and
the mod may be redistributed in modpacks without prior permission. Pull requests are welcome but may be reviewed rarely.
