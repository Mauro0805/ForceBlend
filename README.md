# ForceBlend

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

ForceBlend prepares existing Overworld chunks so Minecraft can blend new terrain beside them after a seed or
generator change, chunk import or deletion. It prepares the existing side of the border; it does not generate
terrain, change seeds or smooth a border whose two sides already exist. Back up important worlds before use.

## 1.3.0 candidate

This development version combines the 1.2.1 mods with a separate Bukkit plugin. It is not a published release.
Use only the file for your platform:

| Artifact | Target |
|---|---|
| `ForceBlend-1.3.0.jar` | Fabric, Minecraft 1.18-1.21.11 and 26.1-26.3 |
| `ForceBlend-neoforge-1.3.0.jar` | NeoForge, Minecraft 1.20.6-26.3 |
| `ForceBlend-bukkit-1.3.0.jar` | Bukkit-compatible server/pre-start tool; see installation requirements below |

Use the Java runtime required by Minecraft. Fabric and Bukkit retain Java 17 bytecode; NeoForge retains Java 21
bytecode and uses Minecraft's LZ4 library. Fabric API is not required; Fabric Mod Menu is optional. Replace any
old ForceBlend Once JAR rather than installing both. Declared ranges are not guarantees for every modpack or fork.

## Using the mods

Once-per-world preparation is the default. Selecting an unprepared world shows affected chunks, regions, deferred
work and estimated processing time. Choose **Prepare world (backup is on)**, **Open without changes** or **Cancel**.
With automatic backup disabled, **Back up and prepare world** remains first and **Prepare without backup (risky)**
is a separate choice. Progress and completion use native Minecraft screens.

Edit `<game>/config/forceblend.properties` or use Fabric Mod Menu. Settings are reread on the next opening/start:

| Setting | Default |
|---|---|
| `run-mode` | `once`; optional `every-open` |
| `automatic-processing` | `true` |
| `automatic-backup` | `true` |
| `detailed-logging` | `false` |
| `maximum-chunks-per-run` | `unlimited` |
| `processing-threads` | `all` |

Completed runs record `forceblend.json`; legacy `forceblendonce.json` markers are honored. Failed, empty or
chunk-limited unfinished runs do not record completion. Use `every-open` after later imports or deletions.
Client backups are enabled by default. Dedicated-server preparation always requires a completed backup.

## Bukkit-compatible servers

**Installing the JAR in `plugins/` alone does not prepare the main world.** Main-world preparation requires a
pre-start command while the server is stopped. If your host cannot run that command before Minecraft, this
primary-world workflow is unavailable. ForceBlend never bypasses a running world's session lock.

1. Stop the server and world editors. Keep an independent backup of important worlds.
2. Put `ForceBlend-bukkit-1.3.0.jar` beside the server JAR. Copy the
   [settings template](bukkit-plugin/src/main/resources/forceblend-settings-and-acceptance.properties) there too.
3. Set `worlds` and the exact `minecraft-version`, read the comments, then set `accept-world-preparation=true`.
4. Run the command below from the server directory. Start Minecraft **only if it exits with code 0**.

```text
java -Xmx128m -jar ForceBlend-bukkit-1.3.0.jar --settings forceblend-settings-and-acceptance.properties
```

Keep this exit-code guard in your startup script or panel; do not start the world after a failed preparation.
The same JAR may optionally be installed in `plugins/` for read-only analysis or explicitly selected offline
worlds. Its configuration is `plugins/ForceBlend/config.yml`; in-server preparation requires ForceBlend as the
only plugin and refuses locked worlds. This restriction does not apply to the stopped-world pre-start route.

The worker defaults to 512 MiB and caps processing concurrency by heap and CPU budget. To change its heap, add
`-Dforceblend.workerHeapMiB=1024` before `-jar`; supported values are 256-4096 MiB. Leave RAM for the server and OS.
The settings template explains worker limits, once/every-open operation and all other options.

Server compatibility is limited to the builds advertised with the final release, not every Bukkit API fork.
Folia, distributed storage and arbitrary world-management/plugin combinations are not supported.

### Removing the plugin

Stop the server, remove its ForceBlend JAR from `plugins/` and remove the pre-start command and dependent exit-code
guard. Restore the normal server launch command. Settings and completion files may be removed; keep backups.
Minecraft does not need ForceBlend installed to read prepared chunks or perform native blending. Removing it
stops every-open automation and unfinished chunk-limited work; it does not undo generated terrain.

## Backups and limits

Backups are full-world ZIPs in `backups/forceblend/<world>/`, outside the source world. Only root `session.lock`
is excluded. Preparation has per-region safeguards, not a single atomic transaction across the whole world.
After a failed application, retain the failed copy and restore a complete backup before retrying.

Only complete Overworld Anvil chunks near missing/incomplete terrain are candidates; deep interiors stay untouched.
Nether, End, custom dimensions, entity/POI files and structure data are not processed. Structures are not repositioned.
CubicChunks, Forge and snapshots are unsupported. Custom generators must preserve compatible storage and native blending.

## Building and contributing

Use JDK 25 and the included Gradle wrapper:

```text
./gradlew build
./gradlew -p bukkit-plugin build
```

On Windows use `gradlew.bat`. Mod JARs are in `fabric/build/libs/` and `neoforge/build/libs/`; the separate plugin is
in `bukkit-plugin/build/libs/`. Do not install sources or `-unbundled` intermediates. Shared code lives in `common/`.
Versions and shared NBT/LZ4 coordinates come from root `gradle.properties`.

Any modpack may include ForceBlend without asking. Forks are welcome under the [MIT license](LICENSE.txt).
This began as a community proof of concept; review, support, updates and release schedules are not guaranteed.
Keep contributions focused and test world-data changes on disposable copies. See the [changelog](CHANGELOG.md).
