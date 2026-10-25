# Changelog

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN (valcheffnet).

## 1.2.1 - 2026-10-25

- The NeoForge file now runs on Minecraft 1.20.6 to 26.3 instead of 26.3 only. It is built for Java 21 and uses
  the lz4 library that Minecraft ships, since a second copy stops NeoForge 1.20.6 and 1.21.1 at startup.
- Fixes leftover `.forceblend-rollback-*.tmp` files in the region folder on Linux and macOS after a failed write.
  The world itself was already restored correctly.

## 1.2.0 - 2026-10-25

Combines the ForceBlend 1.1.0 beta workflow with the ForceBlend Once ports.

### World opening and configuration

- Defaults to once-per-world preparation, with optional every-opening mode for later imports and deletions.
- Honors existing `forceblendonce.json` markers; successful current runs record `forceblend.json`.
- Leaves failed runs, worlds without terrain, and chunk-limited unfinished work unmarked as complete.
- Adds native Minecraft analysis, confirmation, progress and completion screens instead of desktop dialogs.
- Shows affected chunks, regions, deferred work, estimated duration and processing progress.
- Provides optional Fabric Mod Menu settings and a properties file for both loaders.
- Supports configurable processing threads, per-opening chunk limits, automatic processing and detailed logs.

### Backups and server support

- Enables full-world backups by default on clients. With that setting disabled, the backup choice stays first and
  preparing without a backup is explicitly labeled risky.
- Stores ZIPs outside the source world in `backups/forceblend/<world>/`; excludes only the root runtime `session.lock`.
- Adds Fabric dedicated-server preparation before world loading, including `--world` and `--universe` handling.
- Requires a completed backup before every dedicated-server application, regardless of the client backup preference.
- Adds a separate NeoForge artifact for Minecraft 26.3 while sharing processing, backup and configuration code.

### Compatibility and fixes

- Extends the declared Fabric target range through Minecraft 26.3. NeoForge targets 26.3 only.
- Replaces the 26.x local-variable-name injection with a signature-based, cancellable world-open hook.
- Handles the screen-access move from Minecraft to Gui in 26.2 and 26.3.
- Defers world opening until after the completion screen tick, avoiding the observed Fabric 1.18.2
  `Screen cannot be null` crash with Fabric Screen API installed.
- Retains complete-chunk eligibility, seven-chunk frontier selection, marker formats, supported compression formats,
  stale-plan validation and per-region recovery safeguards.
- Removes unused frontier bitsets without changing selection or reporting.
- Separates shared sources in `common/` from the Fabric and NeoForge adapters and builds.

### Verification and limits

- Real Fabric 1.18.2 singleplayer testing covers native screens, preparation, `old_noise`, adjacent terrain,
  playing, saving and reopening after the screen-transition fix.
- Earlier dedicated-server comparisons cover Fabric 1.18.2, 1.19.4, 1.20.4, 1.21.11, 26.1.2, 26.2 and 26.3,
  plus NeoForge 26.3. Final-candidate checks are recorded separately by the maintainers.
- Passing results apply to the tested artifacts and environments, not every intermediate version or mod combination.
- Overworld Anvil terrain only. No Nether/End/custom-dimension processing, retroactive terrain smoothing,
  CubicChunks, Forge, snapshot or Paper support is added.

### Updating from ForceBlend Once

Replace the old JAR with the matching Fabric or NeoForge ForceBlend JAR; do not install both.
Keep the existing world marker and backups. Once mode recognizes the old marker and skips preparation.
Use every-opening mode when future topology changes should trigger another analysis.

## 1.1.0-beta.1

Historical ForceBlend beta, separate from the ForceBlend Once 1.1.0 port releases.

- Added read-only pre-analysis, confirmation choices, progress, ETA and configuration.
- Added complete world backups, optional Mod Menu integration, parallel region processing and deterministic limits.
- Added immutable plans, stale-source checks and recoverable external-chunk replacement.
- Used desktop dialogs; native Minecraft screens replace them in 1.2.0.

## 1.0.0

First stable community release. Verified in game on Minecraft 1.18.2 and 26.2.

- Added a multi-version Fabric artifact with version-correct blending markers and spatial frontier detection.
- Limited new markers to complete Overworld chunks near missing or incomplete terrain, leaving deep interiors alone.
- Supported imported islands, deleted cavities, Anvil compression variants and external `.mcc` streams.
- Preserved chunk status and DataVersion, with temporary-file region replacement and idempotent repeat preparation.
- Adopted the MIT license, unrestricted modpack inclusion and community forks, without guaranteed maintenance.

## Earlier prototypes

Earlier builds were proof-of-concept alphas and betas, including local builds with temporary unsuffixed version
numbers. They are not additional stable releases. Successive fixes corrected the 1.18 `old_noise` schema,
removed unsafe partial-chunk markers and replaced seed-only gating with spatial frontier detection.
