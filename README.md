# ForceBlend

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN (valcheffnet).

ForceBlend prepares existing Overworld chunks so Minecraft can blend new terrain beside them after a seed or
generator change, chunk import, or deletion. Minecraft generates and blends the terrain; ForceBlend prepares
the existing side of the border. It does not retroactively smooth a border whose two sides already exist.

## Version 1.2.0

The `1.2.0` development branch combines the earlier workflow with ForceBlend Once's server and NeoForge support.
Source availability is not a stable-release announcement. Releases use the existing
[CurseForge project](https://www.curseforge.com/minecraft/mc-mods/forceblend-once); older Modrinth files are historical.

| Artifact | Declared target |
|---|---|
| `ForceBlend-1.2.0.jar` | Fabric, stable Minecraft 1.18-1.21.11 and 26.1-26.3 |
| `ForceBlend-neoforge-1.2.0.jar` | Minecraft 26.3, NeoForge 26.3.0.90 or newer within that family |

Install only the file for your loader, replacing any old ForceBlend Once JAR. Fabric API is not required;
Mod Menu is optional on Fabric. Use the Java runtime required by the game. Fabric retains Java 17 bytecode;
the NeoForge build uses Java 25.

The 1.2.0 manual singleplayer test on Fabric 1.18.2 verified native screens, preparation, the `old_noise` marker,
adjacent terrain, saving and reopening. Dedicated-server comparisons are recorded for Fabric 1.18.2, 1.19.4,
1.20.4, 1.21.11, 26.1.2, 26.2 and 26.3, plus NeoForge 26.3. Separate Fabric 26.x client-menu results are also recorded.
These are environment-specific results, not a guarantee for every modpack or every version in the declared ranges.
Final release acceptance remains separate. Detailed evidence and maintainer checklists live in
[forceblend-team](https://github.com/valcheffnet/forceblend-team), subject to repository access.

## Opening a world

Once per world is the default. Selecting an unprepared world starts read-only analysis of affected chunks,
regions, deferred work and estimated processing time. With automatic backup enabled, choose:

- **Prepare world (backup is on)**: back up, prepare, then enter.
- **Open without changes**: enter without applying the plan.
- **Cancel**: return without opening the world.

With automatic backup disabled, preparation becomes two choices: **Back up and prepare world** first, then
**Prepare without backup (risky)**. Processing uses native Minecraft progress screens, not desktop dialogs.

A completed run records `forceblend.json`. Once mode skips later preparation; existing `forceblendonce.json`
markers are honored too. Failed runs, absent terrain and deferred work do not record completion.
Choose every-opening mode when later imports or deletions should trigger fresh analysis.

## Settings and backups

Edit `<game>/config/forceblend.properties`, or use optional Fabric Mod Menu settings.
Values are read again at the next opening or server start.

| Setting | Default |
|---|---|
| `run-mode` | `once` (`every-open` is available) |
| `automatic-processing` | `true` |
| `automatic-backup` | `true` |
| `detailed-logging` | `false` |
| `maximum-chunks-per-run` | `unlimited` |
| `processing-threads` | `all` |

When automatic processing is disabled, clients ask before analysis and dedicated servers skip preparation.
Dedicated servers have no dialogs and always require a completed backup before applying, regardless of the
client backup preference.

Backups are complete world ZIPs under `<game>/backups/forceblend/<world>/`, outside the source world.
Only the root runtime `session.lock` is excluded. Keep a separate, restorable offline copy of important worlds.
Preparation uses per-region safeguards, not an atomic transaction across the entire world.

## Boundaries

Only complete Overworld Anvil terrain chunks within the seven-chunk frontier of missing or incomplete terrain
are blending candidates. Deep interiors stay untouched. The mod preserves chunk status, DataVersion and world
content while changing blending markers and the relevant derived caches; unsafe old markers on partial chunks
can be repaired.

Nether, End, custom dimensions, entity/POI files and structure data are not processed. ForceBlend does not edit
seeds, import/delete chunks, generate terrain or reposition structures. CubicChunks, Forge and snapshots are
unsupported. Terrain generators must retain compatible Anvil storage and Minecraft's native blending path.
Paper remains future work, with no plugin artifact in 1.2.0.

## Building and contributing

Use JDK 25 and the included Gradle wrapper:

```bash
./gradlew build
./gradlew -p fabric build
./gradlew -p neoforge build
```

On Windows, use `gradlew.bat`. The root build builds both loaders and runs the shared tests.
Ordinary release JARs are in `fabric/build/libs/` and `neoforge/build/libs/`.
Shared sources live in `common/`; loader adapters and metadata live in `fabric/` and `neoforge/`.

This started as a community proof of concept. Any modpack may include it without asking, and forks are welcome.
Community changes can use pull requests, but review, support, future updates and release schedules are not guaranteed.
Keep changes focused and test world-data changes only against disposable copies.

ForceBlend is [MIT-licensed](LICENSE.txt). Preserve the original copyright and bundled third-party notices when redistributing.
