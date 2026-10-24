# Compatibility and testing

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

## Artifacts and evidence

| Build | Target | Verification |
|---|---|---|
| ForceBlend 1.0.0 | Fabric 1.18.2 and 26.2 | Previously verified in game. |
| ForceBlend 1.1.0-beta.1 | Fabric declared stable ranges | Automated coverage; gameplay retest pending. |
| ForceBlend Once 1.1.0 | Separate Fabric/NeoForge 26.3 ports | SasnycoN's supplied test record. |
| Merged ForceBlend 1.2.0 Fabric | 1.18-1.21.11 and 26.1-26.3 | Builds and tests pass; dedicated-server blending verified on 1.18.2, 26.1.2, 26.2, and 26.3. Singleplayer and other versions pending. |
| Merged ForceBlend 1.2.0 NeoForge | 26.3, NeoForge 26.3.0.90+ within that family | Build passes; dedicated-server blending verified on 26.3. Singleplayer pending. |

One shared source tree produces separate Fabric and NeoForge JARs. NeoForge is not claimed to support the older Fabric
range. Snapshots, pre-releases, release candidates, Forge, and future versions are excluded.

Fabric's 26.x hook targets `openWorld(String, Runnable)` by descriptor. It no longer depends on a local-variable name.
The older Fabric release families retain their version-selected intermediary adapters.

## Execution environments

Singleplayer uses native Minecraft confirmation, settings, and progress screens. It does not depend on AWT or Swing;
Minecraft's `java.awt.headless=true` setting no longer bypasses confirmation. If native UI initialization fails, opening
is cancelled and the failure is logged. Fabric Mod Menu is optional; properties-file settings work on both loaders.

Dedicated servers run the headless preparation path before world loading. They never use desktop dialogs and always
create a backup before applying. Fabric reads `server.properties` and its world/universe launch options. NeoForge uses
`ServerAboutToStartEvent` for dedicated servers and a cancellable client hook for integrated worlds.

Run mode defaults to once. The existing `forceblendonce.json` marker is honored; every-opening mode ignores markers.
Limited runs only record completion after no planned work remains.

## Automated coverage

The shared suite exercises topology, complete-chunk eligibility, marker formats, compression, malformed inputs,
idempotence, transactional replacement, external-stream rollback, read-only plans, deterministic limits, progress,
configuration persistence, backup safety, and source-change detection.

Integration tests also exercise mandatory headless backups, restore bytes from the ZIP, first/second openings,
legacy-marker migration, every-opening mode, deferred completion, disabled processing, absent worlds, failed backups,
26.x version boundaries, and server world-path resolution.

Client tests verify both Screen lifecycle naming families, widget reinitialization callbacks, replayed world-open
arguments, bypassing the hook during continuation, and propagating Minecraft failures without leaking the bypass.

Build success does not prove lifecycle order, native screens, or terrain appearance inside a running game.

## Measured dedicated-server blending

Additional Fabric 1.18.2 and 26.1.2 startup/save/reopen, repeated-work, chunk-limit, and actual released-mod migration
results are recorded in [the runtime verification report](RUNTIME_VERIFICATION_1.2.0.md).
Measured mean border steps also fell from 37.99 to 1.63 blocks on 1.18.2 and from 36.38 to 1.41 on 26.1.2.
These server cases do not complete the singleplayer checkboxes below.

The exact 1.2.0 artifacts reduced mean old/new border height steps from 36.57 to 1.54 blocks on Fabric 26.2,
37.15 to 1.90 on Fabric 26.3, and 37.15 to 1.91 on NeoForge 26.3. Each comparison measured 1,536 border pairs.
Selection, restored backup bytes, and once-mode restarts also passed. See [the test report](BLEND_TEST_RESULTS.md).
These server tests do not verify native client screens.

## Merged 1.2.0 gameplay matrix

Use disposable worlds and record exact game, loader, Java, operating-system, and mod versions.

- [ ] Fabric 1.18.2: prepare old terrain, generate a border, play, save, and reopen.
- [ ] Fabric 26.1.2, 26.2, and 26.3: verify the shared 26.x hook and all four choices.
- [x] Fabric 26.2 and 26.3 dedicated servers: measured blending, preparation before level loading, restored backup region bytes, and once-mode skipping.
- [ ] NeoForge 26.3 singleplayer: verify all four choices, especially Cancel returning to world selection.
- [x] NeoForge 26.3 dedicated server: measured blending, preparation before level loading, restored backup region bytes, and once-mode skipping.
- [x] Actual released ForceBlend Once 1.1.0 world: Fabric 26.3 dedicated-server once mode skips preparation and creates no new backup or marker.
- [ ] Repeat actual legacy-world skipping through the singleplayer client path.
- [x] Fabric 1.18.2 and 26.1.2 dedicated servers: every-opening mode detects a deleted area on a later startup.
- [x] Fabric 1.18.2 and 26.1.2 dedicated servers: a 128-chunk limit defers completion until the final application.
- [ ] Repeat every-opening mode and chunk-limited continuation through the singleplayer client screens.
- [x] Restore generated test-world backups separately and compare original region bytes; restored worlds were not launched afterward.
- [x] Run the shared automated suite (64 tests) and both loader builds.

[The ecosystem notes](ECOSYSTEM_COMPATIBILITY.md) retain the original performance-mod and generator assessments.
Those historical tests do not verify new mod/version combinations or the merged artifact.

## Repository reorganization

Sources now live in `common/`, `fabric/`, and `neoforge/`. Fabric output moves to `fabric/build/libs/`.
`paper/` is a future-plugin checklist, not a supported runtime. Historical blend-test measurements refer to
exact JAR hashes recorded in the report, not automatically to every rebuilt artifact.

After this layout-only change, the root and independent loader builds passed and all 64 tests passed.
Both release JAR SHA-256 hashes match the earlier blend-test artifacts exactly. No new gameplay test is claimed.
