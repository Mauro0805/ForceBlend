# ForceBlend

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN (valcheffnet on GitHub).

ForceBlend prepares existing Overworld chunks so Minecraft can blend newly generated terrain beside them. Use it
after changing a seed or terrain generator, importing chunks, or deleting areas that will be generated again.
Minecraft does the terrain generation and blending; ForceBlend prepares the existing side of the border.

Version 1.2.0 brings the 1.1.0 workflow and the ForceBlend Once ports into one codebase. The mod name is **ForceBlend**
and the mod ID is `forceblend`. Source is maintained in [Mauro0805/ForceBlend](https://github.com/Mauro0805/ForceBlend).
The 1.2.0 integration is prepared for branch `1.2.0`, based on the `1.1.0` beta.
The maintainer will push the code and the separate team documentation; this is not a published release.
Releases are distributed through the existing [CurseForge project](https://www.curseforge.com/minecraft/mc-mods/forceblend-once).
Older Modrinth files remain historical releases; new releases use CurseForge.

## Choose a build

| Artifact | Target |
|---|---|
| `ForceBlend-1.2.0.jar` | Fabric: Minecraft 1.18-1.21.11 and 26.1-26.3 |
| `ForceBlend-neoforge-1.2.0.jar` | NeoForge 26.3.0.90 or newer within Minecraft 26.3 |

Both builds use the same processing code and support local singleplayer and dedicated servers. Fabric API is not
required. Mod Menu is optional on Fabric. Use the Java version required by the game; the Fabric mod retains Java 17
bytecode, while the NeoForge build uses Java 25.

**Singleplayer and the remaining version checks are still pending in [the test matrix](docs/COMPATIBILITY.md).**
Dedicated-server blending, backups, and once-mode restarts were verified with the exact 1.2.0 artifacts on Fabric 26.2,
Fabric 26.3, and NeoForge 26.3. See [the measured results](docs/BLEND_TEST_RESULTS.md).
Additional server blending, save/reopen, chunk-limit, every-opening, and actual released-mod migration checks are recorded in
[the runtime report](docs/RUNTIME_VERIFICATION_1.2.0.md). These do not certify the pending client screens.
The original 1.0.0 was verified in game on 1.18.2 and 26.2. SasnycoN reported in-game tests of the separate
ForceBlend Once 26.3 Fabric and NeoForge builds. Those results do not verify the merged artifact.

## Opening a world

The default is **once per world**. Until preparation completes, selecting a world starts a read-only analysis that
shows the selected chunks, regions, deferred work, and estimated processing time. You can choose:

- **Apply and enter**: prepare the selected chunks and enter.
- **Back up, apply and enter**: finish a world backup before preparing the chunks.
- **Enter unchanged**: enter without applying the plan.
- **Cancel**: return without opening the world.

Analysis and processing use Minecraft screens, with progress, ETA, active regions, and speed. Preparation runs off the
render thread, and a completion message appears before entering the world. No Swing dialogs or desktop notifications
are required. If the native UI cannot initialize, opening is cancelled and the failure is logged.

A completed once-per-world run writes `forceblend.json` inside the world. Later openings skip preparation.
An existing `forceblendonce.json` from ForceBlend Once also counts as completed, including its older non-strict JSON
format. A failed run or a run with deferred chunks does not write a completion marker. Worlds without existing terrain
are not marked as completed.

Choose **Every opening** when you want subsequent seed changes, imports, or deleted areas to be detected. This mode
ignores both completion markers. To repeat preparation while keeping once mode, close the game and remove both marker
files from a disposable world copy first.

## Settings and backups

Edit `<game directory>/config/forceblend.properties`, or use the ForceBlend settings entry in Fabric Mod Menu.
Settings are read again at the next opening or server start.

| Setting | Default | Meaning |
|---|---|---|
| `run-mode` | `once` | Use `every-open` to scan at every opening. |
| `automatic-processing` | `true` | Clients ask before scanning when false; dedicated servers skip processing. |
| `automatic-backup` | `false` | Clients always back up before applying when true. |
| `detailed-logging` | `false` | Enable region and chunk diagnostics. |
| `maximum-chunks-per-run` | `unlimited` | A positive limit defers remaining work to later openings. |
| `processing-threads` | `all` | Use all processors reported by the JVM, or a smaller positive count. |

**Dedicated-server runs always create a backup before applying**, even when `automatic-backup=false`.
The server path does not use client screens. Fabric resolves `level-name` from `server.properties` and honors
`--world` and `--universe`; NeoForge receives the world path from the server.

Backups go to `<game directory>/backups/forceblend/<world name>/<timestamp>.zip`. Each ZIP contains the complete world
data except the root `session.lock`, which Minecraft recreates. Files created in the same second receive a numeric
suffix. Backups must remain outside the source world.

Keep a restorable offline copy of important worlds. Integrated backups are optional on clients and do not make the
whole preparation an atomic transaction.

## What changes in the world

ForceBlend selects complete chunks within seven chunks of missing or incomplete terrain. This includes outer borders,
deleted holes, and isolated imports while leaving deep interiors untouched.

It writes the version-correct `blending_data` marker, invalidates the derived height/light caches of newly prepared
chunks, and repairs unsafe markers left on incomplete chunks by old development builds. It preserves chunk status,
`DataVersion`, blocks, structures, entities, and POI data. It processes only Overworld Anvil terrain.

Changed regions use temporary files, source validation, and replacement. External `.mcc` streams have rollback
snapshots for ordinary move failures. Earlier regions can already be completed if a later region fails.

Blending is not retroactive. If both sides of a hard border exist, remove the disposable new side before preparation
and generate that side afterward. Terrain generators must retain vanilla Anvil storage and Minecraft's blending path.
CubicChunks is unsupported.

## Moving from ForceBlend Once

Replace the old loader-specific JAR with the matching ForceBlend build. Keep the world and its old
`forceblendonce.json`; once mode honors that marker. The CurseForge project stays the same, so pack references to
that project remain usable. Old world-folder region backups are left in place.

## Building and contributing

The project started as a proof of concept. It remains MIT-licensed, free, and available for any modpack without asking
permission. Forks are welcome. Maintenance, response times, future game versions, and pull-request review are not
guaranteed.

Mauro0805 owns the repository; SasnycoN has the agreed direct-push workflow. Community contributions can use pull
requests. See [CONTRIBUTING.md](CONTRIBUTING.md) for the code boundaries.

Use JDK 25 and the root wrapper:

```bash
./gradlew build
./gradlew -p fabric build
./gradlew -p neoforge build
```

The root command builds both loaders; the `-p` commands build one independently.
Fabric output is in `fabric/build/libs/`; NeoForge output is in `neoforge/build/libs/`. Upload the ordinary JAR, not a sources
JAR. [Compatibility](docs/COMPATIBILITY.md), [scope](docs/PROJECT_SCOPE.md), [architecture](docs/ARCHITECTURE.md),
[world-data safety](SECURITY.md), and the [publishing guide](docs/PUBLISHING.md) describe the remaining checks.

Third-party notices and licenses are included in the source tree and release JARs. Keep the [MIT license](LICENSE.txt)
and original copyright when redistributing the mod.

## Repository layout

Before publishing, follow [the 1.2.0 maintainer checklist](docs/RELEASE_CHECKLIST.md).
Development-branch pushes do not certify the remaining gameplay checks.

- [common/](common/README.md): shared processing, backups, configuration, client workflow, and tests.
- `fabric/`: Fabric entrypoints, mixins, Mod Menu adapter, metadata, and build.
- `neoforge/`: NeoForge entrypoints, client mixin, metadata, and build.
- [paper/](paper/README.md): future-plugin checklist only. No Paper artifact or support in 1.2.0.

The root Gradle composite build coordinates both independent loader builds. Shared sources compile into each JAR,
not an extra dependency. Shared version and library settings remain in the root `gradle.properties`.
