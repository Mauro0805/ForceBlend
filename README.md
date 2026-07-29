# ForceBlend

ForceBlend is a small Fabric client mod and proof of concept for one specific job: preparing existing Overworld chunks
so Minecraft can use its own terrain blending when new terrain is generated beside them.

It is useful after changing a world's seed or terrain generator, importing chunks, or deleting areas that will be
generated again. ForceBlend does not generate terrain, move chunks, or replace Minecraft's blending algorithm.

> **Keep a restorable backup of any world you care about.** ForceBlend edits Anvil region files. The new backup option
> makes this easier, but automatic backups are off by default and no tool can replace a tested offline copy.

## Release status

- **1.0.0** is the stable reference release. It was tested in game and verified working on Minecraft **1.18.2** and
  **26.2**.
- **1.1.0-beta.1** adds the confirmation workflow, progress reporting, parallel processing, settings, and integrated
  backups. Its automated tests pass, but this beta still awaits a fresh in-game test on 1.18.2 and 26.2.

The same JAR declares Fabric compatibility with stable Minecraft 1.18-1.21.11 and 26.1-26.2. Versions other than
1.18.2 and 26.2 are mapping-audited targets, not personally verified runtime environments. Snapshots and future
versions are not included.

ForceBlend is client-side, works only with local singleplayer worlds, and processes only the Overworld. It does not
touch the Nether, the End, custom dimensions, entities, or POI data.

### Requirements

- Fabric Loader 0.12.12 or newer, subject to the Minecraft version's own minimum
- No Fabric API
- Mod Menu is optional
- The Java version already required by Minecraft: Java 17 for older releases, Java 21 for 1.20.5+, and Java 25 for 26.x

## What happens when a world opens

With the default settings, selecting a local world starts a read-only analysis. It counts the chunks and regions that
would change, applies the configured per-opening limit, and estimates the processing time. No world file is modified
during this step.

If there is work to do, ForceBlend shows the result and offers four choices:

- **Apply and enter** — process the selected chunks, then continue opening the world.
- **Back up, apply and enter** — create a complete world ZIP first, process the chunks, then enter.
- **Enter unchanged** — open the world without applying the plan.
- **Cancel** — return without opening or changing the world.

If automatic backup is enabled in the settings, either apply choice creates a backup. If the analysis finds nothing to
change, ForceBlend shows a short notification and opens the world normally.

Large analyses and processing runs use a progress window with percentage, completed chunks, ETA, active region files,
and current speed. Backup progress shows files and bytes copied. A desktop notification is sent when processing
finishes where the operating system supports it.

The scanner considers only complete chunks within Minecraft's seven-chunk blending reach of missing or incomplete
terrain. This finds outer borders, holes made by deleted chunks, and isolated imported areas while leaving deep
interior chunks alone. A per-opening limit defers the remaining eligible chunks until a later opening.

Blending is not retroactive. If both sides of a hard border already exist, delete the unwanted new side, open the world
with ForceBlend, apply the preparation, and only then generate that side again.

## Settings

When Mod Menu is installed, its ForceBlend entry opens the settings dialog. Mod Menu is not a dependency; without it,
the mod keeps working with the defaults and the human-editable file at:

```text
<game directory>/config/forceblend.properties
```

| Setting                    |       Default | Behavior                                                                               |
|----------------------------|--------------:|----------------------------------------------------------------------------------------|
| Analyze automatically      |            On | Run the read-only analysis after selecting a world. When off, ask before analyzing.    |
| Automatic backup           |           Off | Create a complete ZIP before every apply action.                                       |
| Detailed logging           |           Off | Log region-level and chunk-level diagnostic events.                                    |
| Maximum chunks per opening |     Unlimited | Process everything selected by the frontier scan, or defer work beyond a chosen limit. |
| Processing threads         | All available | Use the processors reported by the JVM; explicit smaller values are also available.    |

Integrated backups are stored under:

```text
<game directory>/backups/forceblend/<world name>/<timestamp>.zip
```

Files created in the same second receive a numeric suffix instead of replacing an older backup.

## What ForceBlend changes

For eligible chunks, ForceBlend:

- adds the correct `blending_data` marker for the running Minecraft family;
- removes only the derived `Heightmaps` and `isLightOn` caches when a new marker is written;
- preserves `DataVersion`, chunk status, blocks, entities, structures, and POI data;
- repairs unsafe markers left on incomplete chunks by old development builds;
- writes only regions that actually change.

The core supports GZIP, ZLIB, uncompressed, LZ4, and external `.mcc` chunk streams. Before applying a read-only plan,
it verifies that the region set, file metadata, Anvil headers, and external chunks still match the analysis. Every
changed region is built in a temporary file, validated, flushed, and then moved into place. A stale plan or failed
write stops the world from opening instead of applying uncertain data.

## Installation

1. Make a restorable copy of the world.
2. Put the downloaded ForceBlend JAR in the instance's `mods` folder.
3. Change the seed or generator, or import/delete the desired chunks while the game is closed.
4. Open the local world and review the ForceBlend analysis.
5. Choose how to continue, then generate terrain in the empty area after preparation finishes.

## Project status and community use

ForceBlend began as a proof of concept. Version 1.0.0 is its first stable community release; all earlier numbered
builds were alpha or beta development builds, including internal builds that lacked a pre-release suffix.

I do not promise future updates, support response times, or compatibility with future Minecraft versions. Issues and
pull requests are welcome, but they may be reviewed slowly or not at all. Forks are encouraged if you want to continue,
adapt, or expand the project.

You may include ForceBlend in any modpack without asking for permission. Keep the license and attribution when
redistributing it. The project is available under the [MIT License](LICENSE.txt).

## Development

The processing, configuration, and backup code targets Java 17 bytecode and does not depend on Minecraft classes. The
development workspace uses JDK 25 because the current lifecycle adapter compiles against Minecraft 26.2.

```bash
./gradlew clean build
```

The beta JAR is written to `build/libs/ForceBlend-1.1.0-beta.1.jar`.

To validate against a disposable copy of a real world:

```bash
./gradlew forceBlendWorldCopyTest -PforceblendWorld=/path/to/world -PforceblendMinecraftVersion=1.18.2
```

## More documentation

- [Compatibility and testing](docs/COMPATIBILITY.md)
- [Project scope](docs/PROJECT_SCOPE.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Why the blending markers are safe](docs/BLENDING_COMPATIBILITY.md)
- [Performance mods, world generators, structures, and other loaders](docs/ECOSYSTEM_COMPATIBILITY.md)
- [Publishing guide](docs/PUBLISHING.md)
- [Contributing](CONTRIBUTING.md)
- [Security and world-data safety](SECURITY.md)

Third-party libraries keep their own licenses. Their notices and full license texts are included in the source tree and
inside the release JAR.
