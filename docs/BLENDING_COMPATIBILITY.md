# Blending compatibility research

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

## Why this document exists

This page explains why ForceBlend uses two marker formats and why it marks only complete chunks. MCA Selector is an
important maintained reference, but Minecraft's own data fixers and runtime behavior take priority when they differ.

## Minecraft 1.18-1.18.2

Minecraft 1.18 introduced the root-level chunk format, paletted section biomes, a 384-block generation range, and
`blending_data` for old-world upgrades. Its blending codec contains the boolean `old_noise` field.

The mapped 1.18.2 runtime establishes a strict generation dependency:

1. `ChunkStatus` progresses through `empty`, `structure_starts`, `structure_references`, `biomes`, `noise`, `surface`,
   `carvers`, `liquid_carvers`, `features`, `light`, `spawn`, `heightmaps`, and `full`.
2. `ProtoChunk.getBiomeForNoiseGen` throws `Asking for biomes before we have biomes` when called before the chunk
   reaches `biomes`.
3. `BlendingData` samples height and biome data from chunks marked as old noise.
4. The native `ChunkHeightAndBiomeFix` sets `old_noise=true` only for statuses from `noise` through `full`. Earlier
   statuses are not blending sources.

Consequently, placing `old_noise=true` on a `structure_starts` or `biomes` protochunk can make blending request data
before generation has produced it. This is the direct cause of the beta.2 loading failure.

## Minecraft 1.19 and newer

Snapshot 22w11a replaced the 1.18 codec: `old_noise` was removed, `min_section` and `max_section` were added, and the
existence of `blending_data` became the old-terrain signal. The modern `BlendingDataFix` still excludes the same four
pre-noise statuses: `empty`, `structure_starts`, `structure_references`, and `biomes`.

This makes status gating mandatory on both formats. On 1.18 an unsafe true flag is dangerous; on 1.19+ even an otherwise
valid-looking `blending_data` compound is dangerous when attached to an incomplete chunk.

## MCA Selector comparison

MCA Selector's `ChunkFilter_21w43a.Blending` derives `min_section` and `max_section`, writes `blending_data`, and
removes `Heightmaps` and `isLightOn`. Its implementation annotation begins at DataVersion 2844, which identifies the
root-level chunk layout used by that filter family; it is not proof that every runtime beginning with 1.18 decodes the
same blending payload.

The current method does not inspect `Status`, so ForceBlend applies a stricter completion boundary before the equivalent
marker mutation. MCA Selector also contains later version-specific handling: its 25w02a path temporarily downgrades
`DataVersion` because same-version forced blending stopped working in 25w09a, and its 1.21.5 RC2 path restores the
earlier marker method after the game fix. These exceptions reinforce the need to audit each release family rather than
infer behavior from a single implementation threshold.

## ForceBlend policy

- Chunks below DataVersion 2844 are left to Minecraft's native data fixer.
- Only chunks whose normalized status is `minecraft:full` are marked for blending.
- Unsafe ForceBlend markers already present on any partial, missing, unknown, or modded status are removed without
  changing caches or status.
- Complete chunks use `old_noise: 1b` on 1.18-1.18.2 and section bounds on 1.19+.
- `DataVersion` and `Status` are never changed.
- The policy accepts both namespaced and unnamespaced vanilla status values.
- A complete chunk is selected only when current topology places missing or incomplete terrain within the native maximum
  seven-chunk source offset.

Minecraft's native data fix accepts statuses from `noise` through `full` because it must migrate partially generated
historical chunks. ForceBlend's same-version seed/generator workflow is narrower: only `full` chunks are stable
player-visible terrain that must be preserved as the old side of a border. Leaving protochunks unmarked lets the active
generator finish them normally and prevents unnecessary blending sources.

## Native spatial reach

The mapped Minecraft 1.18.2 `Blender.getBlender(ChunkRegion)` bytecode derives its height/biome chunk radius from
`BiomeCoords.fromChunk(7)` and iterates every offset from `-7` through `+7` on both axes. A complete source outside that
square cannot contribute to a newly generated center chunk.

The mapped Minecraft 26.2 `Blender.of(WorldGenRegion)` bytecode retains `HEIGHT_BLENDING_RANGE_CHUNKS = 7`, scans the
same bounded offsets, and applies an additional radial cutoff. ForceBlend uses the seven-chunk square for the universal
stable artifact: it is exact for the currently critical 1.18 family and a conservative superset of the modern bounded
query.

Status filtering and spatial filtering solve separate problems. Status filtering prevents protochunk crashes; spatial
filtering avoids marking complete terrain that native blending cannot currently consult. ForceBlend recomputes this
frontier on every requested analysis because MCA imports and deletions can change topology without changing the seed
or `level.dat`. Once mode skips completed worlds; every-opening mode requests a fresh analysis each time.
Small imported islands are fully selected, deleted cavities select their surrounding band, and deep interiors remain
untouched.

## Primary references

- [Minecraft Java Edition 1.18 release notes](https://feedback.minecraft.net/hc/en-us/articles/4415128577293-Minecraft-Java-Edition-1-18)
- [Minecraft Java Edition snapshot 22w11a technical changes](https://feedback.minecraft.net/hc/en-us/articles/4849829699597-Minecraft-Java-Edition-Snapshot-22w11a)
- [Fabric intermediary mapping build pipeline](https://wiki.fabricmc.net/documentation%3Abuild_pipelines)
- [MCA Selector 21w43a chunk filter](https://github.com/Querz/mcaselector/blob/master/src/main/java/net/querz/mcaselector/version/java_1_18/ChunkFilter_21w43a.java)
- [MCA Selector 25w02a blending workaround](https://github.com/Querz/mcaselector/blob/master/src/main/java/net/querz/mcaselector/version/java_1_21/ChunkFilter_25w02a.java)
- [MCA Selector 1.21.5 RC2 blending restoration](https://github.com/Querz/mcaselector/blob/master/src/main/java/net/querz/mcaselector/version/java_1_21/ChunkFilter_1_21_5_RC2.java)
