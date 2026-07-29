# Compatibility and testing

ForceBlend uses one Java 17 processing core and five small Fabric lifecycle adapters. At startup, the mod enables only
the adapter that matches the running Minecraft family.

## Read the status before choosing a build

| Build            | Status           | Evidence                                                                        |
|------------------|------------------|---------------------------------------------------------------------------------|
| **1.0.0**        | Stable reference | Verified in game on Minecraft **1.18.2** and **26.2**.                          |
| **1.1.0-beta.1** | Pre-release      | Automated tests pass; fresh in-game tests on 1.18.2 and 26.2 are still pending. |

“Included in the metadata range” is not the same as “verified in game.” The project uses these terms consistently:

| Term               | Meaning                                                                                              |
|--------------------|------------------------------------------------------------------------------------------------------|
| Verified in game   | The exact build loaded, processed a real world, opened it, and its result was observed in Minecraft. |
| Mapping-audited    | The lifecycle target and marker family were reviewed, but no full gameplay test was recorded.        |
| Automated coverage | Loader-neutral behavior passed tests outside a complete gameplay session.                            |

Automated coverage is useful evidence for the shared core, but it cannot confirm that every client lifecycle hook,
desktop dialog, mod combination, or generated border behaves correctly at runtime.

## Declared Minecraft range

| Minecraft      | Runtime evidence from stable 1.0.0 | Adapter notes                                                  |
|----------------|------------------------------------|----------------------------------------------------------------|
| **1.18.2**     | **Verified in game**               | Uses the 1.18 `old_noise` marker.                              |
| **26.2**       | **Verified in game**               | Uses the Mojang-official `WorldOpenFlows` family.              |
| 1.18-1.18.1    | Mapping-audited                    | Shares the 1.18 marker and lifecycle family.                   |
| 1.19-1.20.2    | Mapping-audited                    | Uses the first intermediary `WorldOpenFlows` family.           |
| 1.20.3-1.20.4  | Mapping-audited                    | Uses the intermediary backup/open-flow family.                 |
| 1.20.5-1.21.11 | Mapping-audited                    | Uses the later intermediary `WorldOpenFlows.openWorld` family. |
| 26.1           | Bytecode-audited                   | Shares the official-name adapter family with 26.2.             |

The metadata range is closed at 1.21.11 and 26.2. Snapshots, pre-releases, release candidates, and future Minecraft
versions are not included.

The 1.1 beta retains this declared range, but none of its new confirmation, backup, progress, configuration, or
parallel-processing behavior should be described as verified in game until the beta itself is tested.

## Runtime requirements

- Fabric Loader; Fabric API is not required.
- A local singleplayer world with vanilla Anvil Overworld storage.
- A graphical desktop environment for confirmation, progress, and settings dialogs.
- Mod Menu only if the user wants its settings entry; ForceBlend does not require it.

If a desktop UI cannot be created, ForceBlend chooses the safe fallback and opens the world unchanged rather than
applying without confirmation.

## Automated coverage in the 1.1 beta

The suite covers both the established terrain rules and the new workflow, including:

- marker format, complete-chunk eligibility, seven-chunk frontier selection, imports, and deleted cavities;
- malformed data, compression formats, external chunks, idempotence, and transactional failure;
- read-only planning, deterministic per-opening limits, and reuse of the plan during application;
- parallel region execution and progress snapshots;
- stale region-set, file, header, chunk-outcome, and external-payload rejection;
- configuration defaults, parsing, persistence, and atomic replacement;
- complete ZIP creation, collision handling, cancellation, cleanup, path safety, and source-change detection.

Real-save copy tests remain important because they exercise actual Anvil data without modifying the supplied source.
They still do not replace a gameplay test for the exact beta artifact.

## Community test checklist for 1.1.0-beta.1

Use a disposable copy, then report what actually happened:

1. Generate a visible area, close the game, and make an offline backup.
2. Change the seed or generator, or delete/import chunks in the copy.
3. Install the beta and select the world.
4. Confirm that the read-only analysis reports plausible chunk and region counts.
5. Test **Enter unchanged** and verify that the world opens without ForceBlend writes.
6. Reopen, choose a backup-and-apply path, and verify the ZIP and progress display.
7. Generate the empty border, inspect the transition, save, and reopen.
8. Repeat with a chunk limit if practical and confirm deferred work appears on a later opening.
9. Check the log for ForceBlend errors.

Include the exact ForceBlend, Minecraft, Fabric Loader, Java, operating-system, Mod Menu, and other mod versions. The
[ecosystem assessment](ECOSYSTEM_COMPATIBILITY.md) discusses performance mods, terrain generators, structures,
snapshots, Forge, and NeoForge separately; it should not be read as an unrecorded runtime guarantee.
