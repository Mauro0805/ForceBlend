# ForceBlend 1.2.0 blending verification

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

## Outcome

The supplied `blend-test` procedure confirms real terrain blending with the merged **1.2.0** artifacts on
Fabric 26.2, Fabric 26.3, and NeoForge 26.3 dedicated servers. No blending-code change was needed for these cases.
The mod version remains `1.2.0`.

These are live server-generation tests, not only checks that `blending_data` exists.
They do not verify the native singleplayer screens or every supported Minecraft version.

## Border measurements

Absolute height differences in blocks between adjacent old/new columns, using the supplied `OCEAN_FLOOR` decoder.
Each row compares **1,536 border column pairs**, with 560 old full chunks and 1,920 final full chunks per world.

| Environment | Control mean | ForceBlend mean | Mean reduction | Mod median | Mod p90 | Mod steps of 8+ |
|---|---:|---:|---:|---:|---:|---:|
| Fabric 26.2 | 36.57 | 1.54 | 95.8% | 0 | 6 | 7.2% |
| Fabric 26.3 | 37.15 | 1.90 | 94.9% | 0 | 7 | 9.6% |
| NeoForge 26.3 | 37.15 | 1.91 | 94.9% | 0 | 7 | 10.0% |

The no-mod worlds had steps of at least eight blocks in 89.8% of border pairs on 26.2 and 91.3% on both 26.3 runs.
The ForceBlend means are close to the reference harness's earlier 1.5-block and 1.9-block means.
This is not a promise of a perfectly flat border: the largest measured step with the mod was still 25 blocks.
The results apply to this seed pair and setup, not all terrain generators, modpacks, or structures.

## Selection and safety checks

- [x] Exactly 476 old chunks were marked in every mod world; controls had no markers.
- [x] The marked set exactly matched the seven-chunk frontier of the rectangular old-terrain baseline.
- [x] All 84 deep-interior chunks remained unmarked.
- [x] No incomplete chunk had a blending marker after generation.
- [x] All marker section bounds remained `min_section=-4` and `max_section=20`.
- [x] Preparation and backup completed before the server's `Preparing level` message.
- [x] Each server created one full-world ZIP before applying.
- [x] Each ZIP was extracted to a separate directory; all four Overworld region files matched the exact
  pruned pre-processing input, byte for byte.
- [x] Once-mode completion was recorded, and the next startup skipped preparation without creating another backup.
- [x] Both controls and mod worlds saved and stopped normally.
- [x] Both builds pass; the final shared suite passed all 64 tests.

The restored worlds were checked at the file level, not launched afterward. Existing user worlds were not used.
Earlier automated runs encountered the already-recorded intermittent Windows temporary-directory cleanup failure.
The unchanged suite passed on retry; no cleanup failures were suppressed.

## Reproduction setup

- Verification date: 2026-10-23.
- Windows; Oracle GraalVM Java `25.0.4.1.1`; Gradle `9.6.1`.
- Fabric Loader `0.19.5`, using bare Fabric Loom `1.17.17` projects.
- NeoForge `26.3.0.90`, using a bare ModDevGradle `2.0.148` project.
- Dedicated servers bound to localhost, with separate no-mod and mod worlds.
- Default ForceBlend settings: automatic processing on, once mode, unlimited chunks, all available processors,
  optional backup preference off. The dedicated-server path still required a backup.
- The NeoForge test reused the same saved vanilla 26.3 seed-A baseline as the Fabric test, then used its own
  NeoForge no-mod control.

Procedure:

1. Generate vanilla terrain with seed `12345`, forcing chunks `x=-8..15, z=-8..7`.
2. Save and stop the server. Copy the world twice and remove only incomplete terrain entries from those copies.
   The resulting baseline has 560 full chunks.
3. Change both copies to seed `67890` in `data/minecraft/world_gen_settings.dat`.
4. Start the mod copy with the appropriate 1.2.0 JAR. Confirm preparation and the backup precede level loading.
5. In each copy, force chunks `x=-18..25, z=-18..17`, using batches below the command limit.
   Allow generation, save, and stop before inspecting region files.
6. Use the supplied `blendtool.py` to compare `OCEAN_FLOOR` heights across the original border.
   The supplied `render.py` produces top-down heightmap comparisons, not client screenshots.
7. Restore and compare each backup, then restart the prepared world to verify once-mode skipping.

The Windows run used isolated orchestration around the supplied Python tools rather than executing the Bash runner's
directory-deletion steps. Always use disposable, separate test projects; the original `run.sh` removes named test worlds.
These measurements describe terrain continuity, not processing speed, FPS, or memory usage.

## Exact artifacts

| File | SHA-256 |
|---|---|
| `ForceBlend-1.2.0.jar` | `4d98d3f0c018a1849b66517ace97470c38508268110dc8eaeabd0edaeeffc942` |
| `ForceBlend-neoforge-1.2.0.jar` | `daf68776906d1279aa0384187aa5fab38a90db1edeecdf2a7e9470117fe47234` |

## Still unverified

Singleplayer confirmation, cancellation, progress and Mod Menu behavior; the merged 1.18.2 and 26.1.2 paths;
runtime migration of an actual ForceBlend Once world; repeated migrations in every-opening mode;
and chunk-limited continuation in game remain on [the compatibility checklist](COMPATIBILITY.md).
