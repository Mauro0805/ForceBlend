# Publishing ForceBlend

This page contains ready-to-use project information for GitHub, Modrinth, and CurseForge.

## Project details

| Field                    | Value                                            |
|--------------------------|--------------------------------------------------|
| Name                     | ForceBlend                                       |
| Slug                     | `forceblend`                                     |
| Author                   | `Mauro0805`                                      |
| Version                  | `1.0.0`                                          |
| File                     | `ForceBlend-1.0.0.jar`                           |
| Loader                   | Fabric                                           |
| Environment              | Client; local singleplayer only                  |
| Declared Minecraft range | Stable 1.18-1.21.11 and 26.1-26.2                |
| Verified in game         | 1.18.2 and 26.2                                  |
| Fabric API               | Not required                                     |
| License                  | MIT; modpack use and forks allowed               |
| Source                   | `https://github.com/Mauro0805/forceblend`        |
| Issues                   | `https://github.com/Mauro0805/forceblend/issues` |

Upload the normal JAR, not `-sources.jar`.

## Short description

> Prepare existing Overworld chunks for Minecraft's native terrain blending after changing a seed, generator, or chunk
> layout.

## Modrinth and CurseForge description

> **ForceBlend** is a client-side Fabric utility and proof of concept for world migrations. It prepares existing
> Overworld chunks so Minecraft's native terrain blending can smooth the border beside newly generated terrain after a
> seed or generator change, a chunk import, or a chunk deletion.
>
> Back up the world first. Change the world while the game is closed, install ForceBlend, open the local world once,
> and then generate the empty area. Blending is not retroactive if both sides of a hard border already exist.
>
> ForceBlend rebuilds the current terrain frontier on every world open. It prepares only complete chunks within seven
> chunks of missing or incomplete terrain, including outer borders, deleted cavities, and imported islands. Deep
> interiors and incomplete protochunks remain unmarked.
>
> One JAR declares compatibility with stable Minecraft 1.18-1.21.11 and 26.1-26.2. **Minecraft 1.18.2 and 26.2 are the
> versions verified in game.** Other listed releases are mapping-audited compatibility targets and welcome community
> testing.
>
> Requirements: Fabric Loader only. Fabric API and configuration libraries are not required. ForceBlend processes only
> local singleplayer Overworld region files; it does not touch the Nether, the End, custom dimensions, entities, or POI
> data.
>
> This project may not receive future updates or quick support. Issues and pull requests are welcome but may be reviewed
> rarely. Forks are encouraged, and ForceBlend may be included in any modpack without asking permission. The project is
> licensed under MIT.

## 1.0.0 release notes

> ForceBlend 1.0.0 is the first stable community release. Earlier builds were alpha and beta proof-of-concept versions.
>
> - Verified in game on Minecraft 1.18.2 and 26.2.
> - One Fabric JAR for the declared stable ranges from 1.18 through 1.21.11 and 26.1 through 26.2.
> - No Fabric API or configuration dependency.
> - Continuous frontier detection for external borders, imported islands, and deleted cavities.
> - Only complete chunks inside Minecraft's seven-chunk blending reach are prepared.
> - Correct marker formats for 1.18 and 1.19+ without changing `DataVersion`.
> - Transactional region replacement and repeatable no-op scans.
> - Support for vanilla Anvil compression formats and external chunks.
> - MIT license, unrestricted modpack inclusion, and community forks.
>
> Always make a restorable backup before use.

## Platform settings

### Modrinth

- Client side: required
- Server side: unsupported
- Dependency: Fabric Loader
- Fabric API: not required
- Version type: Release
- Select exact declared stable game versions; do not select snapshots or future releases

### CurseForge

- Project type: Mod
- Suggested category: Utility or World Gen
- Mod loader: Fabric
- Release type: Release
- Select exact declared stable game versions

## Release checklist

- [ ] Run `./gradlew clean build` with JDK 25.
- [ ] Confirm that the artifact is `build/libs/ForceBlend-1.0.0.jar`.
- [ ] Verify `fabric.mod.json`, embedded libraries, and third-party notices inside the JAR.
- [ ] Publish the compatibility wording exactly: only 1.18.2 and 26.2 are verified in game.
- [ ] Keep the backup warning visible.
- [ ] Add a project icon and screenshots if available; they are helpful, not release blockers.
- [ ] Calculate and publish the SHA-256 checksum.
- [ ] Tag the repository as `v1.0.0`.
- [ ] Download the published file and test that exact artifact in a disposable world.
