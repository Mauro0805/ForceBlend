# Changelog

This file records meaningful ForceBlend changes. The project follows [Semantic Versioning](https://semver.org/) from
the first stable community release onward.

## 1.2.0 - Testing phase

Development branch: `1.2.0`, starting from the `1.1.0` beta.

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

- Documents manual client acceptance, migration, development pushes, and release gates in a maintainer checklist.
- Updates the team CurseForge drafts for 1.2.0 completion markers, full-world backups, and configuration.
- Verifies dedicated-server blending and execution modes on Fabric 1.18.2 and 26.1.2.
- Verifies migration from a world processed by the released ForceBlend Once 1.1.0 build on a Fabric 26.3 server.
- Client acceptance remains pending. See [the additional runtime results](docs/RUNTIME_VERIFICATION_1.2.0.md).
- Separates shared sources into `common/` and loader adapters into `fabric/` and `neoforge/`.
- Coordinates both loader builds from the root; moves Fabric output to `fabric/build/libs/`.
- Reserves `paper/` for a future-plugin checklist only; no Paper support is added in 1.2.0.
- Updates ignore rules for generated output, private tooling, and local world backups.
- Integrates the 1.1.0 workflow with the 26.3 and dedicated-server ports from ForceBlend Once.
- Keeps the ForceBlend name, `forceblend` mod ID, and `backups/forceblend/` backup location.
- Defaults to once per world, with `run-mode=every-open` and a matching settings choice.
- Honors legacy `forceblendonce.json` markers and records current completion in `forceblend.json`.
- Does not record completion when a run fails, has no existing terrain, or leaves deferred work.
- Replaces the 26.x variable-name injection with a cancellable `openWorld(String, Runnable)` hook.
- Replaces Swing dialogs with native Minecraft screens, including Mod Menu settings, progress, and completion.
- Runs client analysis and application off the render thread, then resumes the intercepted world-open call.
- Extends the Fabric target range through Minecraft 26.3 and adds headless dedicated-server preparation.
- Adds a separate NeoForge 26.3 build that shares the core, backup, configuration, and client workflow.
- Fixes the client screens on Minecraft 26.2 and 26.3, where `setScreen` and the current screen moved from
  `Minecraft` to `Gui`. Before the fix, an unprepared world could not be opened on those versions.
- Turns `automatic-backup` on by default and puts the backup choice first, since players tend to take the first button.
  Without automatic backup the menu offers Back up and prepare world, Prepare without backup (risky), Open without
  changes, and Cancel.
- Requires a full-world backup for every headless application; omits the root runtime `session.lock` from ZIPs.
- Adds tests for legacy markers, execution modes, deferred completion, mandatory backups, and server paths.
- Uses CurseForge for new releases and credits both maintainers in loader metadata.
- Verifies 1.2.0 dedicated-server blending on Fabric 26.2/26.3 and NeoForge 26.3 with a seed-swap control comparison,
  exact frontier selection, restored-backup region bytes, and once-mode restart checks.
- Singleplayer and other runtime checks remain pending. See [the measured results](docs/BLEND_TEST_RESULTS.md).

## 1.1.0-beta.1 - 2026-07-29

This beta keeps the same terrain-preparation scope as 1.0.0 and focuses on making world opening safer, clearer, and
more controllable.

### User experience

- Added a read-only pre-analysis that counts scanned chunks, affected chunks, deferred chunks, and affected regions
  before any write.
- Added an estimated processing time based on the current analysis, with a conservative fallback when no useful sample
  exists.
- Added four post-analysis choices: apply and enter; back up, apply and enter; enter unchanged; or cancel.
- Added live analysis, backup, validation, and processing progress for larger jobs.
- Progress can show percentage, chunks or regions completed, ETA, active region files, and chunk throughput.
- Added a completion notification where desktop notifications are available.

### Configuration and backup

- Added optional Mod Menu integration without making Mod Menu a runtime requirement.
- Added persistent settings for automatic analysis, automatic backup, detailed logs, per-opening chunk limits, and
  processing threads.
- Defaults are automatic analysis on, automatic backup off, detailed logs off, unlimited chunks, and all available
  processors.
- Settings are stored in `<game>/config/forceblend.properties` using atomic replacement.
- Added complete world ZIP backups under `<game>/backups/forceblend/<world>/<timestamp>.zip`.
- Backups reject unsafe links and overlapping source/destination paths, keep timestamp collisions, clean partial files,
  and are discarded if the world changes during creation.

### Processing and safety

- Split processing into a reusable immutable plan and an apply phase, so the confirmation dialog does not require a
  second full analysis.
- Added deterministic per-opening limits; deferred chunks remain eligible on later world opens.
- Region analysis, stale-plan validation, and application can use multiple worker threads while each region file still
  has a single writer.
- Added stale-plan checks for the region set, file metadata, Anvil headers, planned chunk outcomes, and external chunk
  payloads before replacement.
- Preserved temporary-file region assembly, forced writes, validation, unchanged-region avoidance, and cleanup on
  failure.
- Added recoverable multi-file commits for changed external `.mcc` chunks, restoring the original sidecars and region
  if any replacement move fails.

### Verification status

- The automated test suite passes for the `1.1.0-beta.1` code line.
- This beta has **not yet been re-tested in game** on 1.18.2 or 26.2. Do not treat its automated status as a gameplay
  verification.
- Stable 1.0.0 remains the release verified in game on Minecraft 1.18.2 and 26.2.
- No performance benchmark claim is made for this beta.

## 1.0.0 - 2026-07-29

First stable community release. All earlier builds were proof-of-concept alpha or beta builds, including internal
builds whose version numbers did not carry a pre-release suffix.

### Main features

- One Fabric JAR for the declared stable ranges from Minecraft 1.18 through 1.21.11 and 26.1 through 26.2.
- Version-selected lifecycle adapters and version-correct blending markers.
- Continuous spatial frontier detection without a seed check, saved state, command, or request file.
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
- Real-save copy tests completed repeat passes for 1.18.2 and 26.2 without modifying either source world.

### Community release

- Relicensed under MIT.
- Modpack inclusion is allowed without prior permission.
- Forks are encouraged.
- Pull requests may be accepted, but review and future updates are not guaranteed.

## Pre-release history

- **Beta 5:** replaced seed/state gating with continuous spatial frontier detection.
- **Beta 4:** limited preparation to complete chunks and introduced real-save identity testing.
- **Beta 3:** repaired unsafe markers on incomplete chunks and improved processing reports.
- **Beta 2:** added the correct Minecraft 1.18 `old_noise` format.
- **Beta 1:** introduced the universal JAR, lifecycle adapter selection, and loader-neutral processing core.
- **Alpha prototypes:** established the Fabric hook, Overworld-only boundary, native blending trigger, tests, and
  project structure. Some local alpha builds used `1.0.0`, `1.1.0`, or `1.2.0` as temporary identifiers; they were not stable
  public releases.
