# ForceBlend

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

ForceBlend prepares existing Overworld chunks so Minecraft can blend new terrain beside them after a seed or
generator change, chunk import or deletion. It prepares the existing side of the border; it does not generate
terrain, change seeds or smooth a border whose two sides already exist. Back up important worlds before use.

## Downloads

Get the files from the [releases page](https://github.com/Mauro0805/ForceBlend/releases) and install only the one
for your platform:

| File | Platform |
|---|---|
| `ForceBlend-1.3.0.jar` | Fabric, Minecraft 1.18-1.21.11 and 26.1-26.3 |
| `ForceBlend-neoforge-1.3.0.jar` | NeoForge, Minecraft 1.20.6-26.3 |
| `ForceBlend-bukkit-1.3.0.jar` | Paper, Purpur and Spigot servers (read the server section below first) |

Use the Java version your Minecraft version needs. The Fabric and Bukkit files run on Java 17 or newer, the NeoForge
file on Java 21 or newer. Fabric API is not needed and Mod Menu is optional. If you still have the old ForceBlend
Once JAR, remove it, since the two should not be installed together. Big modpacks and forks may behave differently,
so try a copy of your world first.

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

## Paper, Purpur and Spigot servers

**Putting the JAR in `plugins/` does not prepare your main world.** Minecraft locks the main world while the
server runs, and ForceBlend never works around that lock. The main world is prepared by running the same JAR as a
command before the server starts. If your host does not let you run a command before Minecraft, you cannot prepare
the main world with the plugin.

1. Stop the server and close any world editors. Keep your own backup of worlds you care about.
2. Put `ForceBlend-bukkit-1.3.0.jar` next to the server JAR, together with the settings file
   [`forceblend-settings-and-acceptance.properties`](https://github.com/Mauro0805/ForceBlend/releases/download/v1.3.0/forceblend-settings-and-acceptance.properties)
   from the release.
3. Fill in `worlds` and the exact `minecraft-version`, read the comments in the file, then set
   `accept-world-preparation=true`.
4. From the server directory run the command below, and start Minecraft **only if it exits with code 0**.

```text
java -Xmx128m -jar ForceBlend-bukkit-1.3.0.jar --settings forceblend-settings-and-acceptance.properties
```

Put that exit-code check in your startup script or panel, so a failed preparation never leads to the world being
opened. You can also put the JAR in `plugins/`, where it can analyze worlds without changing them or prepare
extra worlds that are not loaded. Those settings are in `plugins/ForceBlend/config.yml`. Preparing from inside the
server only works when ForceBlend is the only plugin, and it refuses any world that is locked. The command before
startup has no such limit.

The preparation runs in a separate process with 512 MiB of memory by default and uses as many threads as that
memory and your CPU allow. To give it more memory, add `-Dforceblend.workerHeapMiB=1024` before `-jar` (256 to 4096
MiB), and leave enough RAM for the server and the OS. The settings file explains every other option.

Version 1.3.0 was tested once on every Minecraft release from 1.18 to 26.3, each time on Paper, Purpur or Spigot.
1.20.3, 1.21.2 and 26.1 were not tested because no server build for them was available. Folia, other forks and
setups with world-management plugins are not supported.

> [!CAUTION]
> ### Paper 1.21.9 can hang when the server stops, even without ForceBlend. This is a Paper bug, so ForceBlend 1.3.0 was not tested on 1.21.9. Use Paper 1.21.10 or newer.

### Removing the plugin

Stop the server, delete the ForceBlend JAR from `plugins/`, and take the ForceBlend command and its exit-code check
out of your startup script so the server starts the normal way again. You can delete the settings and completion
files too, but keep the backups. Minecraft reads prepared chunks and blends new terrain without ForceBlend
installed. After removal, every-open runs and unfinished chunk-limited work stop, and terrain that was already
generated stays as it is.

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
