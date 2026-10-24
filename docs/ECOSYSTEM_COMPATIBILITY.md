# Ecosystem compatibility

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

The recorded 1.18.2 sessions below used stable ForceBlend 1.0.0. The merged 1.2.0 artifacts need fresh combination tests.

## Evidence labels

ForceBlend uses the following labels so documentation never implies testing that did not happen:

- **Architecturally compatible:** the projects operate on separate systems and no static overlap was found.
- **Format-compatible candidate:** the world generator is expected to keep vanilla Anvil column storage, but a migration
  smoke test is still required.
- **Dedicated test required:** the project changes the chunk pipeline or I/O deeply enough that source-level separation
  is not sufficient evidence.
- **Unsupported:** a core storage or terrain assumption conflicts with ForceBlend.

These labels keep expectations honest. Exact Minecraft, loader, and mod versions still matter.

## Performance mods

ForceBlend edits closed Overworld region files before the selected world loads, including the dedicated-server preparation path. It does not replace rendering, game
logic, the live chunk scheduler, or Minecraft's region classes.

| Mod             | Assessment                  | Reason                                                                                                                                                                          |
|-----------------|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sodium          | Verified together on 1.18.2 | The supplied 1.18.2 test session loaded Sodium 0.4.1, completed processing, played, saved, and exited normally.                                                                 |
| ImmediatelyFast | Verified together on 1.18.2 | The supplied test used ImmediatelyFast 1.1.12.                                                                                                                                  |
| FerriteCore     | Verified together on 1.18.2 | The supplied test used FerriteCore 4.2.1.                                                                                                                                       |
| Lithium         | Verified together on 1.18.2 | The supplied test used Lithium 0.10.3.                                                                                                                                          |
| C2ME            | Dedicated test required     | C2ME explicitly changes chunk generation, loading, serialization, and I/O. ForceBlend runs before those systems start, but the generated-border result must be tested together. |

The same 1.18.2 session also included EntityCulling, LazyDFU, Mod Menu, and Starlight. That is evidence for the exact
versions in the test log, not a blanket guarantee for every release combination.

Primary project references:

- [Sodium](https://github.com/CaffeineMC/sodium)
- [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast)
- [FerriteCore](https://github.com/malte0811/FerriteCore)
- [Lithium](https://github.com/CaffeineMC/lithium)
- [C2ME](https://github.com/RelativityMC/C2ME-fabric)

## Terrain and biome generators

ForceBlend does not interpret biome registries, blocks, noise routers, or structure definitions. It marks complete
vanilla-format chunks and lets the currently installed generator produce new terrain. Consequently, a generator can be
compatible only when it keeps the vanilla Anvil column format and Minecraft's native blending path.

| Generator                | Assessment                  | Operational requirement                                                                                                                                                                         |
|--------------------------|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Terralith                | Format-compatible candidate | Keep the same datapack/mod installed while generating the new border; test a disposable copy.                                                                                                   |
| Biomes O' Plenty         | Format-compatible candidate | Keep its exact required dependencies and registries installed; test the target release.                                                                                                         |
| Oh The Biomes We've Gone | Format-compatible candidate | Keep its exact required dependencies and registries installed; test the target release.                                                                                                         |
| CubicChunks              | Unsupported                 | CubicChunks changes the vertical chunk/storage model, which conflicts with ForceBlend's vanilla 16x16 column and Anvil section-range assumptions. Do not run ForceBlend on a CubicChunks world. |

The first three entries are not yet runtime-certified. Terrain shape, biome transitions, custom blocks, and structures
remain the responsibility of Minecraft and the active generator.

Primary project references:

- [Terralith](https://github.com/Stardust-Labs-MC/Terralith)
- [Biomes O' Plenty](https://github.com/Glitchfiend/BiomesOPlenty)
- [Oh The Biomes We've Gone](https://github.com/Potion-Studios/Oh-The-Biomes-Weve-Gone)
- [CubicChunks](https://github.com/OpenCubicChunks/CubicChunks)

## Structures at a blended border

Floating, clipped, buried, or poorly supported villages and temples can occur at an old/new border even when native
blending is active. Native blending smooths terrain density, surface height, and biomes; it does not redesign a
structure start or guarantee support beneath every piece after two generators meet. Mojang's own 1.18 pre-release notes
described surface and biome blending as supported while warning that cave results could vary.

This is normally a native blending/world-generation limitation, not proof that ForceBlend corrupted a chunk. ForceBlend
must not move structure starts or rewrite structure references because doing so would expand the project from marker
preparation into destructive world-generation editing.

Recommended mitigation:

1. Choose deletion/generation borders away from important structures where possible.
2. Remove a wider disposable buffer around a problematic structure, then regenerate it consistently with the new
   generator.
3. Repair isolated visual defects manually after the migration.
4. Always test the chosen generator and seed on a copy before editing the primary save.

References:

- [Minecraft 1.18 Pre-Release 1 blending notes](https://feedback.minecraft.net/hc/en-us/articles/4413339550733-Minecraft-Java-Edition-1-18-Pre-Release-1)
- [Minecraft snapshot 22w11a 3D blending changes](https://feedback.minecraft.net/hc/en-us/articles/4849829699597-Minecraft-Java-Edition-Snapshot-22w11a)

## Snapshots, pre-releases, and release candidates

Fabric Loader can run snapshots, but that does not make one ForceBlend binary safe across them. Blending was absent in
21w39a, appeared during the 1.18 snapshot cycle, and its stored schema changed again in 22w11a. Snapshot lifecycle
methods, intermediary identifiers, Java requirements, data versions, and NBT codecs can change weekly.

Supporting **selected audited snapshot bands** is technically possible. Supporting **every blending-era snapshot in the
stable universal JAR** is not a responsible compatibility promise. The recommended design is a separate experimental
artifact and test matrix, with an adapter/profile added only after each snapshot family is audited. Stable metadata
remains closed and excludes snapshots.

References:

- [21w39a: blending not yet available](https://feedback.minecraft.net/hc/en-us/articles/4410294651405-Minecraft-Java-Edition-Snapshot-21w39a)
- [21w43a: first old/new terrain blending iteration](https://feedback.minecraft.net/hc/en-us/articles/4412156111629-Minecraft-Java-Edition-Snapshot-21w43a)
- [22w11a: 3D blending and marker-schema change](https://feedback.minecraft.net/hc/en-us/articles/4849829699597-Minecraft-Java-Edition-Snapshot-22w11a)
- [Fabric-supported Minecraft versions](https://docs.fabricmc.net/players/faq)
- [Fabric intermediary build pipeline](https://wiki.fabricmc.net/documentation%3Abuild_pipelines)

## Forge and NeoForge

The 1.2.0 integration now provides separate Fabric and NeoForge artifacts from one shared source tree.
NeoForge currently targets Minecraft 26.3 only. Forge and a combined multi-loader JAR remain unimplemented.

Most of ForceBlend's processing core is already loader-neutral and can be shared. Loader-specific work remains for
metadata, client lifecycle interception, mappings, build tooling, and validation:

| Delivery model                                                             | Difficulty | Recommendation                                                                                        |
|----------------------------------------------------------------------------|------------|-------------------------------------------------------------------------------------------------------|
| One repository, shared core, separate Fabric/Forge/NeoForge JARs           | Medium     | Recommended. It isolates loader metadata and adapters while reusing the processing and tests.         |
| One physical JAR for Fabric, Forge, and NeoForge on one narrow game family | High       | Possible in theory, but fragile class loading and metadata interactions add little user value.        |
| One physical JAR for all three loaders across 1.18-26.2                    | Very high  | Not recommended. Loader APIs, mappings, Java baselines, and game internals multiply the test surface. |

Fabric discovers root `fabric.mod.json`; Forge discovers `META-INF/mods.toml`; NeoForge uses
`META-INF/neoforge.mods.toml` and a JavaFML entrypoint. Multi-loader tooling such as Architectury demonstrates the
professional common-core/separate-platform-module model. A single repository can still publish coordinated artifacts
with identical ForceBlend behavior and version numbers.

References:

- [Fabric mod metadata](https://docs.fabricmc.net/develop/loader/fabric-mod-json)
- [Forge mod files](https://docs.minecraftforge.net/en/latest/gettingstarted/modfiles/)
- [NeoForge mod files](https://docs.neoforged.net/docs/gettingstarted/modfiles/)
- [Architectury Loom multi-loader tooling](https://github.com/architectury/architectury-loom)
