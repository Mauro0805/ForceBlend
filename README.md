# ForceBlend

ForceBlend is a small Fabric client mod and proof of concept for one specific job: preparing existing Overworld chunks
so Minecraft can use its own terrain blending when new terrain is generated beside them.

It is useful after changing a world's seed or terrain generator, importing chunks, or deleting areas that will be
generated again. ForceBlend does not create terrain or replace Minecraft's blending algorithm.

> **Back up your world first.** ForceBlend edits Anvil region files. Test important worlds on a copy and make sure your
> backup can actually be restored.

## Compatibility

The same JAR can load on Fabric across these stable Minecraft ranges:

- 1.18 through 1.21.11
- 26.1 through 26.2

Only **Minecraft 1.18.2 and 26.2 have been tested in game and verified working**. The other listed releases have audited
lifecycle mappings and share the tested processing core, but they should be treated as community-tested compatibility,
not as a promise from me.

ForceBlend is client-side, works only with local singleplayer worlds, and processes only the Overworld. It does not
touch the Nether, the End, custom dimensions, entities, or POI data.

### Requirements

- Fabric Loader 0.12.12 or newer, subject to the Minecraft version's own minimum
- No Fabric API
- No configuration library
- The Java version already required by Minecraft: Java 17 for older releases, Java 21 for 1.20.5+, and Java 25 for 26.x

## Installation and use

1. Make a restorable backup of the world.
2. Put `ForceBlend-1.0.0.jar` in the instance's `mods` folder.
3. Change the seed or generator, or import/delete the desired chunks while the game is closed.
4. Open the local world once and let ForceBlend finish its scan.
5. Generate terrain in the empty area. Minecraft will blend it against the prepared border.

ForceBlend scans the current terrain frontier every time the world is opened. There is no seed check, one-time state,
command, or manual trigger. This matters because chunk imports and deletions can change a world without changing its
`level.dat`.

The scan prepares only complete chunks within Minecraft's seven-chunk blending reach of missing or incomplete terrain.
That includes the outer border, holes created by deleted chunks, and isolated imported areas. Chunks deep inside an
already generated area are left alone.

Blending cannot repair a hard border after both sides have already been generated. Delete the unwanted new side and
open the world with ForceBlend before generating it again.

## What ForceBlend changes

For eligible chunks, ForceBlend:

- adds the correct `blending_data` marker for the running Minecraft family;
- removes only the derived `Heightmaps` and `isLightOn` caches when a new marker is written;
- preserves `DataVersion`, chunk status, blocks, entities, structures, and POI data;
- repairs unsafe markers left on incomplete chunks by old development builds;
- validates a complete region before replacing it through a temporary file.

It supports GZIP, ZLIB, uncompressed, LZ4, and external `.mcc` chunk streams. Unchanged chunks and regions are not
rewritten.

## Project status

ForceBlend began as a proof of concept and version 1.0.0 is its first stable community release. Earlier numbered builds
were alpha or beta development builds, even where an early internal version number did not include a suffix.

I do not promise future updates, support response times, or compatibility with future Minecraft versions.
Issues and pull requests are welcome, but they may be reviewed slowly or not at all. Forks are encouraged if you want to
continue, adapt, or expand the project.

You may include ForceBlend in any modpack without asking for permission. Please keep the license and attribution when
redistributing the mod. The project is available under the [MIT License](LICENSE.txt).

## Development

The processing core is independent of Minecraft classes and targets Java 17 bytecode. The development workspace uses
JDK 25 because it compiles the current lifecycle adapter against Minecraft 26.2.

```bash
./gradlew clean build
```

The release JAR is written to `build/libs/ForceBlend-1.0.0.jar`.

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
