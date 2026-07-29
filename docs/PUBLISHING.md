# Publishing ForceBlend

This page contains ready-to-use project information for GitHub, Modrinth, and CurseForge. It is intentionally cautious
about the difference between the stable release and the new beta.

## Project details

| Field                    | Value                                            |
|--------------------------|--------------------------------------------------|
| Name                     | ForceBlend                                       |
| Slug                     | `forceblend`                                     |
| Author                   | `Mauro0805`                                      |
| Current file version     | `1.1.0-beta.1`                                   |
| File                     | `ForceBlend-1.1.0-beta.1.jar`                    |
| Version type             | Beta / pre-release                               |
| Stable reference         | `1.0.0`                                          |
| Loader                   | Fabric                                           |
| Environment              | Client; local singleplayer only                  |
| Declared Minecraft range | Stable 1.18-1.21.11 and 26.1-26.2                |
| Verified in game         | Stable 1.0.0 on 1.18.2 and 26.2                  |
| Beta runtime status      | Automated tests passed; in-game retest pending   |
| Fabric API               | Not required                                     |
| Mod Menu                 | Optional                                         |
| License                  | MIT; modpack use and forks allowed               |
| Source                   | `https://github.com/Mauro0805/forceblend`        |
| Issues                   | `https://github.com/Mauro0805/forceblend/issues` |

Upload the normal JAR, not `-sources.jar`. Keep stable 1.0.0 available while the beta awaits runtime validation.

## Short description

> Review and prepare existing Overworld chunks for Minecraft's native terrain blending after seed, generator, or chunk
> layout changes.

## Modrinth and CurseForge description

> **ForceBlend** is a client-side Fabric utility and proof of concept for world migrations. It prepares existing
> Overworld chunks so Minecraft's native terrain blending can smooth the border beside newly generated terrain after a
> seed or generator change, a chunk import, or a chunk deletion.
>
> When you select a local world, ForceBlend first performs a read-only analysis. It shows how many chunks and regions
> would change and an approximate processing time. You can apply and enter, create a full backup before applying, enter
> unchanged, or cancel. Large jobs include progress, ETA, active regions, speed, and a completion notification where
> desktop notifications are available.
>
> Processing can use multiple region workers while keeping one writer per region. Before writing, ForceBlend verifies
> that the analyzed region files are still current. Changed regions retain the existing temporary-file and validation
> safeguards; unchanged regions are not rewritten.
>
> Settings cover automatic analysis, automatic backup, detailed logs, a per-opening chunk limit, and processing
> threads. Defaults are automatic analysis on, unlimited chunks, backups off, detailed logs off, and all available
> processors. Mod Menu provides an optional settings entry but is not required.
>
> ForceBlend prepares only complete chunks within seven chunks of missing or incomplete terrain. It processes local
> singleplayer Overworld region files only; it does not touch the Nether, End, custom dimensions, entities, or POI data.
> Blending is not retroactive when both sides of a hard border already exist.
>
> One JAR declares compatibility with stable Minecraft 1.18-1.21.11 and 26.1-26.2. Stable ForceBlend 1.0.0 was verified
> in game on Minecraft 1.18.2 and 26.2. **Version 1.1.0-beta.1 has passed automated tests but still awaits a fresh
> in-game test on both versions.** Other listed releases are mapping-audited targets.
>
> Keep a restorable offline backup. Integrated ZIP backups are optional and disabled by default.
>
> This project may not receive future updates or quick support. Issues and pull requests are welcome but may be reviewed
> rarely. Forks are encouraged, and ForceBlend may be included in any modpack without asking permission. The project is
> licensed under MIT.

## 1.1.0-beta.1 release notes

> This beta makes ForceBlend easier to understand before it edits a world.
>
> - Read-only world analysis with chunk, region, deferred-work, and time estimates.
> - Four choices: apply; back up and apply; enter unchanged; or cancel.
> - Parallel per-region analysis, validation, and application with deterministic limits.
> - Progress percentage, ETA, active regions, speed, backup progress, and completion notification.
> - Optional Mod Menu settings for automatic analysis, backup, detailed logs, limits, and worker threads.
> - Atomic human-editable configuration at `<game>/config/forceblend.properties`.
> - Complete timestamped ZIP backups at `<game>/backups/forceblend/<world>/`.
> - Stale-plan validation before transactional region replacement.
> - No Fabric API requirement and no new hard configuration dependency.
>
> The automated suite passes. In-game verification of this beta on 1.18.2 and 26.2 is still pending, so publish it as a
> beta rather than a stable release. No performance benchmark claim is included.

## Platform settings

### Modrinth

- Client side: required
- Server side: unsupported
- Dependency: Fabric Loader
- Optional dependency: Mod Menu
- Fabric API: not required
- Version type: **Beta**
- Select only the exact declared stable Minecraft versions; do not select snapshots or future releases

### CurseForge

- Project type: Mod
- Suggested category: Utility or World Gen
- Mod loader: Fabric
- Release type: **Beta**
- Select only the exact declared stable Minecraft versions

## Beta release checklist

- [ ] Run `./gradlew clean build` with JDK 25.
- [ ] Confirm that the automated suite passes without ignored product failures.
- [ ] Confirm the artifact is `build/libs/ForceBlend-1.1.0-beta.1.jar`.
- [ ] Inspect `fabric.mod.json`, embedded libraries, and third-party notices inside the JAR.
- [ ] Confirm the settings file and backup path on a disposable instance.
- [ ] Keep the wording explicit: stable 1.0.0 is verified in game; this beta is not yet re-verified.
- [ ] Keep the offline-backup warning visible and state that automatic backup defaults to off.
- [ ] Do not publish a speedup claim without a reproducible benchmark.
- [ ] Calculate and publish the SHA-256 checksum.
- [ ] Tag the repository as `v1.1.0-beta.1` and mark the GitHub release as a pre-release.
- [ ] Download the published file and test that exact artifact before promoting any later stable release.
