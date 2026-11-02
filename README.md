# ForceBlend

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN (valcheffnet).

ForceBlend prepares existing Overworld chunks so Minecraft can blend new terrain beside them after a seed or
generator change, chunk import, or deletion. Minecraft generates and blends the terrain; ForceBlend prepares
the existing side of the border. It does not retroactively smooth a border whose two sides already exist.

## Version 1.2.0

See the [changelog](CHANGELOG.md) for release changes and earlier version history.

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
The mod artifacts contain no Bukkit plugin; use the separate Bukkit JAR below.

## Bukkit plugin 1.2.1

A separate `bukkit-plugin/` build uses the same shared preparation core. The same JAR is a conventional
Bukkit plugin and a standalone pre-start tool. The pre-start route handles the server's main world while the
server is stopped; it does not need to load Bukkit or disable the server's other plugins. No NMS is used.
This is a separate server artifact, not a replacement for the Fabric or NeoForge files.

Build with `./gradlew -p bukkit-plugin build`. Install only
`bukkit-plugin/build/libs/ForceBlend-bukkit-1.2.1.jar`, not the `-unbundled.jar` intermediate.
For main worlds, copy the fully commented
[forceblend-settings-and-acceptance.properties](bukkit-plugin/src/main/resources/forceblend-settings-and-acceptance.properties)
template beside the server JAR. It contains every setting, acceptance, examples and startup/removal instructions:

Keep the server and editors stopped, keep a separate recoverable world copy, then set
`accept-world-preparation=true`. There is no dialog. Run this before your existing server start command:

```bash
java -Xmx128m -jar ForceBlend-bukkit-1.2.1.jar --settings forceblend-settings-and-acceptance.properties
```

Set `worlds=world` and the exact saved `minecraft-version` first. Relative `world-container` paths resolve from
the settings file. `worlds` accepts comma-separated names; use `worlds.0`, `worlds.1`, etc. instead for names containing
commas. Existing `--world-container`, repeatable `--world` and `--minecraft-version` overrides remain available.
Only start the server if this command exits successfully (exit code 0). On PowerShell, check `$LASTEXITCODE`;
on Bash, chain the server command with `&&`. A hosting panel must allow a custom pre-start command; installing
the plugin alone cannot prepare its already-locked main world.

Existing `forceblend-prestart.properties` files remain recognized; rename yours without resetting its values if
you want the clearer name. With default discovery, keeping both files is an error: keep one, or select one explicitly
with `--settings`. An explicitly named missing settings file is an error, not a fallback to defaults.

Once mode skips completed worlds. `every-open` rechecks each invocation; a chunk limit defers work until the next
invocation. All six settings and their defaults match the 1.2.1 dedicated-server mod. `unlimited`/`0` mean no chunk
limit; `all`/`auto`/`0` use all available processors. Explicit thread counts range from 1 to 256 and are capped at
available processors. Invalid settings fail closed rather than silently changing the workload.
Read-only analysis is available with `--action analyze` and does not require acceptance or create backups.
`--action unchanged` explicitly skips preparation. Processing logs counts, active regions, ETA, backup progress
and completion. Settings may be selected with `--settings`; command-line tuning overrides the properties values.
Full backups are mandatory, outside the world under `backups/forceblend/<world>/`, even with `automatic-backup=false`,
matching the mod's dedicated-server policy. `preparation-enabled=false` and `analysis-enabled=true` select analysis
from the file alone; when both are false it skips. Preparation takes precedence when both are true.
`automatic-processing=false` skips automatic work. Pre-start preparation defaults on behind acceptance; the
in-server adapter defaults it off because it must not rewrite an already-locked main world.

The in-server adapter remains optional: explicit offline targets in `plugins/ForceBlend/config.yml`, with
`preparation-enabled: true`, still require ForceBlend as the only plugin. `analysis-enabled` is read-only.
When using pre-start for the main world, leave in-server `preparation-enabled` false; preparation has already run.
Both paths require existing session locks, an exact Minecraft metadata-version match and local storage supporting
hard links and exclusive locks. Running worlds are refused, never unlocked or edited behind Minecraft's back.
A bounded helper JVM owns both the locks and all preparation writes.

The verified support matrix is deliberately narrow: Windows with local Anvil storage, Spigot build
3498-Spigot-b081915-10ba1be on 1.18.2, Paper 388/Purpur 1632 on 1.18.2, and Paper 132/Purpur 2568 on 1.21.11.
Use Java 17 for these 1.18.2 servers and Java 21 for these 1.21.11 servers. Other builds and forks are not verified.
This matrix is independent of Fabric/NeoForge compatibility. Java 17 bytecode and a Bukkit 1.18 API baseline do
not establish a universal game range.

Acceptance covers settings/removal, primary-world pre-start, backup restoration, and numerical seed-swap blending
on those five combinations. Native storage checks cover external `.mcc` streams on both version families and
LZ4 on 1.21.11; 1.18.2 does not support LZ4. Minecraft may normalize compression/storage on its next save.
Interruption and simulated write failures are tested on disposable worlds, including restoration and reopening.
An interrupted application may have committed some regions: do not bypass a failed startup guard. Stop all
writers, retain the failed copy, and restore a complete backup to a separate folder before retrying.
Linux, arbitrary forks/custom generators, distributed storage, Folia and broad in-server plugin combinations
remain unverified or unsupported. Installing only the plugin cannot safely prepare an already-locked main world.
See the team repository for exact evidence and limitations; publication remains a maintainer action.

### Removing the plugin

Stop the server, remove the ForceBlend JAR from `plugins/`, and remove its pre-start command from your startup script
or panel. Restore the normal server command directly, including removing any `&&` or exit-code guard that depended
on the deleted command. No uninstall command or world conversion is needed. Configuration files may be removed too.
Keep backups until you are satisfied with the world. Existing preparation uses Minecraft's own blending tags;
removal does not undo terrain changes or require deleting those tags. The world completion/legacy marker files
are optional after removal, not required by Minecraft.

The world can still load/save after removal, including after a chunk-limited run. Deferred chunks will not be
prepared automatically, and every-open mode stops running: keep ForceBlend until pending work is done if you need
that preparation. Future seed/generator changes or imports may require running it again. Removal tests do not
guarantee compatibility with every generator or certify visual terrain quality.

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
