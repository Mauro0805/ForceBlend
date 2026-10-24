# Publishing ForceBlend

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN (valcheffnet).

Source lives in [Mauro0805/ForceBlend](https://github.com/Mauro0805/ForceBlend).
New files use the existing [CurseForge project](https://www.curseforge.com/minecraft/mc-mods/forceblend-once),
project ID `1746239`, with the chosen display name **ForceBlend**. Preserve the project ID and slug so existing
modpacks keep their project reference. New Modrinth publication is outside this release plan.

## Release metadata

| Field | Value |
|---|---|
| Version | `1.2.0`, unreleased integration |
| Development branch | `1.2.0`, starting from the `1.1.0` beta |
| Name / mod ID | ForceBlend / `forceblend` |
| Maintainers | Mauro0805 and SasnycoN |
| Fabric file | `ForceBlend-1.2.0.jar` |
| NeoForge file | `ForceBlend-neoforge-1.2.0.jar` |
| Fabric Minecraft range | 1.18-1.21.11 and 26.1-26.3 |
| NeoForge Minecraft range | 26.3 only; NeoForge 26.3.0.90+ within that family |
| Environments | Singleplayer and dedicated server |
| Default run mode | Once per world; every opening is configurable |
| Backups | `<game>/backups/forceblend/<world>/` |
| Fabric API | Not required |
| Mod Menu | Optional, Fabric only |
| License | MIT; modpacks need no prior permission |

Keep the merged build unpublished until the gameplay matrix is completed. If a preview is deliberately distributed
before that, mark its platform release type as Beta and make the pending runtime checks explicit.

## Short description

> Prepare existing Overworld chunks for native terrain blending, with client confirmation, server backups, and
> configurable once-per-world execution.

## CurseForge description

> **Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.**
>
> ForceBlend prepares existing Overworld chunks so Minecraft can blend new terrain beside them after seed or generator
> changes, chunk imports, or deletions.
>
> On clients, a read-only analysis reports affected chunks, regions, and an approximate duration. Back up and prepare,
> prepare without a backup, open without changes, or cancel. Large jobs show progress, ETA, regions, and speed. Fabric Mod Menu
> provides optional settings; both loaders can use the properties file.
>
> Dedicated servers prepare worlds before loading them and always make a complete world backup before applying.
> Backups are stored under `backups/forceblend/`, outside the source world.
>
> Once per world is the default. Every opening is available for worlds that need repeated migrations. Existing
> `forceblendonce.json` markers are honored. Chunk-limited runs continue on later starts until their planned work is
> complete.
>
> Fabric targets stable Minecraft 1.18-1.21.11 and 26.1-26.3. The separate NeoForge artifact targets Minecraft 26.3.
> Install the file for your loader and replace the old ForceBlend Once JAR.
>
> ForceBlend processes existing Overworld Anvil terrain only. Blending happens when the empty side generates; it cannot
> smooth a border whose two sides already exist. Keep a restorable offline copy of important worlds.
>
> The code is MIT-licensed, free, available for any modpack, and open to forks. Future updates and prompt support are
> not guaranteed.
>
> The merged 1.2.0 build still requires its recorded gameplay matrix before a stable release.

## Release notes

- Integrate the 1.1.0 workflow with SasnycoN's 26.3, server, and NeoForge work.
- Use a signature-based 26.x client hook with cancellation support.
- Use native Minecraft confirmation and progress screens instead of Swing dialogs.
- Share preparation, backup, completion state, configuration, and UI between loader adapters.
- Add headless Fabric and NeoForge dedicated-server processing with mandatory full-world backups.
- Default to once per world, honor legacy markers, and offer every-opening mode.
- Retain `forceblend`, the ForceBlend name, and `backups/forceblend/`.
- Credit both maintainers in loader metadata and preserve MIT licensing.

## Before publishing

For the separate development-push, singleplayer, migration, and publication steps, use
[the 1.2.0 maintainer checklist](RELEASE_CHECKLIST.md). Pushing source is not a stable-release claim.

1. Complete [the merged gameplay matrix](COMPATIBILITY.md).
2. Build both artifacts from branch `1.2.0` using JDK 25 and inspect metadata, nested libraries, licenses, and checksums.
3. Update the CurseForge display name and Source link to the canonical repository.
4. Keep "Original mod by Mauro0805" at the top of the description and both authors in the project metadata.
5. Verify author membership and the agreed reward split through the maintainers' account settings.
6. Publish the GitHub release, then the two loader-specific files on the existing CurseForge project.
7. Download and verify the published files before a modpack adopts them.
8. Archive valcheffnet/forceblend-once only after its integration is merged and the canonical source link is live.

Publishing, account changes, pushes, and archiving remain explicit maintainer operations. SasnycoN may push directly;
community changes can continue through pull requests.

## Build layout

Run `./gradlew build` at the root for both loaders and the shared tests, or use `./gradlew -p fabric build`
and `./gradlew -p neoforge build` independently. Upload `fabric/build/libs/ForceBlend-1.2.0.jar` and
`neoforge/build/libs/ForceBlend-neoforge-1.2.0.jar`, not sources JARs or stale root `build/libs/` outputs.
`paper/` is planning only and produces no release artifact.
