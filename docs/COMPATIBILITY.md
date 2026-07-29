# Compatibility and testing

ForceBlend 1.0.0 uses one Java 17 processing core and five small Fabric lifecycle adapters. At startup, the mod enables
only the adapter that matches the running Minecraft family.

## The important distinction

“Included in the supported range” does not mean “personally tested in game.” The project uses three clear levels:

| Level              | What it means                                                                                                     |
|--------------------|-------------------------------------------------------------------------------------------------------------------|
| Verified in game   | The mod loaded, processed a real world, the world opened, and the result was observed in Minecraft.               |
| Mapping-audited    | The lifecycle target and version-specific marker format were checked, but no complete gameplay test was recorded. |
| Automated coverage | The shared Anvil/NBT core passed its test suite and real-save copy tests outside the game.                        |

## Current status

| Minecraft      | Status               | Notes                                                                                                                                                                                                 |
|----------------|----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **1.18.2**     | **Verified in game** | Tested with Java 17 and Fabric Loader 0.19.3. The latest run scanned 15,406 chunks, prepared 2,768 frontier chunks, opened the integrated server, played and saved normally, then exited with code 0. |
| **26.2**       | **Verified in game** | Client startup, world opening, processing, and the generated border were manually checked.                                                                                                            |
| 1.18-1.18.1    | Mapping-audited      | Uses the same 1.18 `old_noise` marker family; community testing is welcome.                                                                                                                           |
| 1.19-1.20.2    | Mapping-audited      | Uses the first `WorldOpenFlows` adapter family.                                                                                                                                                       |
| 1.20.3-1.20.4  | Mapping-audited      | Uses the backup/open-flow adapter family.                                                                                                                                                             |
| 1.20.5-1.21.11 | Mapping-audited      | Uses the later `WorldOpenFlows.openWorld` adapter family.                                                                                                                                             |
| 26.1           | Bytecode-audited     | Shares the official-name adapter family with verified 26.2.                                                                                                                                           |

The metadata range is deliberately closed at 1.21.11 and 26.2. Snapshots, pre-releases, release candidates, and future
versions are not included.

## What the automated tests cover

The 25-test suite covers marker selection, complete-chunk eligibility, the seven-chunk frontier, deep-interior
exclusion, imported islands, deleted cavities, late imports, partial-marker repair, negative and extreme coordinates,
transactional failure, idempotence, and the supported compression formats.

Real-save copy tests have also exercised 1.18.2 and 26.2 data without modifying the source worlds. They provide strong
evidence for the shared processing core, but they are not presented as gameplay tests for every version in between.

## A useful community test

If you test another version, use a disposable world:

1. Generate a visible area and close the game.
2. Back up the world.
3. Change the seed or generator, or delete/import chunks in the copy.
4. Install ForceBlend and open the copy.
5. Confirm that the log reports a completed frontier scan.
6. Generate the empty border and inspect the transition.
7. Save, reopen, and confirm there are no ForceBlend errors.

When reporting a result, include the exact Minecraft, Fabric Loader, Java, operating-system, and other mod versions.
The [ecosystem assessment](ECOSYSTEM_COMPATIBILITY.md) covers performance mods, terrain generators, structures,
snapshots, Forge, and NeoForge separately.
