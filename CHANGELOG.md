# Changelog

This file records meaningful ForceBlend changes. The project follows [Semantic Versioning](https://semver.org/) from
the first stable community release onward.

## Unreleased

No changes yet.

## 1.0.0 - 2026-07-29

First stable community release. All earlier builds were proof-of-concept alpha or beta builds, including early internal
builds whose version numbers did not carry a pre-release suffix.

### Main features

- One Fabric JAR for the declared stable ranges from Minecraft 1.18 through 1.21.11 and 26.1 through 26.2.
- Version-selected lifecycle adapters and version-correct blending markers.
- Automatic frontier detection on every world open, without a seed check, saved state, command, or request file.
- Selection of complete chunks only within Minecraft's seven-chunk blending reach of missing or incomplete terrain.
- Detection of outer borders, deleted cavities, and imported terrain islands while leaving deep interiors untouched.
- Loader-neutral Anvil/NBT processing with GZIP, ZLIB, uncompressed, LZ4, and external `.mcc` support.
- Transactional region replacement, coordinate validation, safe failure behavior, and binary-idempotent repeat scans.
- No Fabric API requirement; Java 17 mod bytecode.

### Safety fixes carried forward from pre-release testing

- Uses `old_noise: 1b` on Minecraft 1.18-1.18.2 and section bounds on Minecraft 1.19+.
- Never writes a blending marker to an incomplete chunk.
- Removes unsafe markers left on partial chunks by old alpha/beta builds without changing their status or caches.
- Preserves `DataVersion` and invalidates only derived height/light caches when preparing an eligible chunk.
- Leaves pre-1.18 chunks to Minecraft's native data fixer.
- Restricts processing to local singleplayer Overworld terrain regions.

### Verification

- Minecraft **1.18.2** and **26.2** were tested in game and verified working.
- The latest 1.18.2 run scanned 15,406 chunks, prepared 2,768 frontier chunks, opened the world, played and saved
  normally, and exited with code 0.
- Twenty-five automated tests cover topology, marker safety, imports, cavities, partial chunks, malformed input,
  transactional failure, idempotence, and compression.
- Real-save copy tests completed repeat passes for 1.18.2 and 26.2 without modifying either source world.

### Community release

- Relicensed under MIT.
- Modpack inclusion is allowed without prior permission.
- Forks are encouraged.
- Pull requests may be accepted, but review and future updates are not guaranteed.

## Pre-release history

The pre-release line evolved quickly while the safe behavior was being discovered:

- **Beta 5:** replaced seed/state gating with continuous spatial frontier detection.
- **Beta 4:** limited preparation to complete chunks and introduced real-save identity testing.
- **Beta 3:** repaired unsafe markers on incomplete chunks and improved processing reports.
- **Beta 2:** added the correct Minecraft 1.18 `old_noise` format.
- **Beta 1:** introduced the universal JAR, lifecycle adapter selection, and loader-neutral processing core.
- **Alpha prototypes:** established the initial Fabric hook, Overworld-only boundary, native blending trigger, tests,
  and
  project structure. Some local alpha builds used `1.0.0`, `1.1.0`, or `1.2.0` as temporary identifiers; they were not
  stable public releases.
