# 1.2.0 additional runtime verification

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

Verification date: 2026-10-24. These are disposable, localhost-only dedicated-server tests on Windows.
They do not certify client screens, Mod Menu, or singleplayer play/save/reopen.

## Startup, saving, execution modes, and limits

Official Fabric server launchers used Fabric Loader 0.19.5. Minecraft 1.18.2 ran on Azul Java 17.0.20;
26.1.2 ran on Oracle GraalVM Java 25.0.4.1.1.
Each baseline was generated without the mod using seed 12345, saved, stopped, copied, and pruned of incomplete
Overworld chunks. Tests used independent copies; no existing user saves were modified.

| Check | Fabric 1.18.2 | Fabric 26.1.2 |
|---|---|---|
| First preparation and full-world backup | Pass; 518 markers | Pass; 476 markers |
| Version-specific marker | `old_noise` present | Section bounds present |
| Save, stop, and once-mode reopen | Pass; skipped, no second backup | Pass; skipped, no second backup |
| Limit of 128 chunks per opening | 128, 128, 128, 128, 6 | 128, 128, 128, 92 |
| Completion only after deferred work finishes | Pass; fifth start | Pass; fourth start |
| Backup before each limited application | Five backups | Four backups |
| Every-opening mode after deleting four chunks while stopped | 89 additional chunks prepared | 68 additional chunks prepared |
| Every-opening completion state | No once-mode marker written | No once-mode marker written |

All test servers saved and stopped normally. The repeated-work cases used two configured processing threads,
not a performance benchmark. These tests establish runtime continuation and processing, not client gameplay.

## Migration from an actual released ForceBlend Once build

An isolated Fabric 26.3 server first ran the official ForceBlend Once 1.1.0 release on a disposable vanilla baseline.
That released mod created its own `forceblendonce.json` and world-folder region backup.
The old JAR was removed and replaced with the exact ForceBlend 1.2.0 Fabric artifact.

- The 1.2.0 startup logged once-mode skipping.
- The original legacy marker bytes remained unchanged.
- No `forceblend.json` or new ForceBlend backup was created.
- The old region backup remained present.
- The server saved and stopped normally.

This verifies a real released-mod marker, not a hand-written fixture. Whole region files are not claimed to stay
byte-identical across Minecraft startup and saving; the game can update them independently of ForceBlend.
Preparation-level no-write behavior is separately covered by the automated legacy-marker test.

| Artifact | SHA-256 |
|---|---|
| Released `ForceBlendOnce-1.1.0.jar` | `64422b4717dcfcf3d88625ec37ff076a762b9c3ada36084f7d51fc892a144078` |
| Current `ForceBlend-1.2.0.jar` | `4d98d3f0c018a1849b66517ace97470c38508268110dc8eaeabd0edaeeffc942` |

The legacy artifact came from the original repository's `v1.1.0` GitHub release.

## Measured blending on the two additional versions

Both copies had their seeds changed from 12345 to 67890 while stopped. The mod copy retained the prepared old
terrain; the control had no mod. Both generated the same ring, x=-18..25 and z=-18..17, then saved and stopped.
The supplied `blendtool.py` decoded `OCEAN_FLOOR` heights across old/new border columns.

| Fabric version | Border pairs | Mean without mod | Mean with mod | Mod median | Mod maximum |
|---|---:|---:|---:|---:|---:|
| 1.18.2 | 1,632 | 37.9859 | 1.6330 | 0 | 30 |
| 26.1.2 | 1,536 | 36.3770 | 1.4063 | 0 | 25 |

These are actual terrain-generation comparisons, not marker-only tests. They are one seed-pair scenario, not a
guarantee of cliff-free results or compatibility with all generators. No processing-speed claim is made.
The released artifacts required no code change and remain version 1.2.0.
Raw results are in [verification/1.2.0-runtime.json](verification/1.2.0-runtime.json).

## Memory and cleanup

Test servers ran sequentially with a 2 GiB Java heap cap. After memory monitoring began, 100 available-RAM samples
ranged from 8.63 to 10.86 GiB on a 31.93 GiB system. These are sampled observations, not a peak-memory benchmark or
a guarantee about unrelated applications. No out-of-memory error was found in the test logs.
All test servers saved and stopped. The two test-owned Gradle daemons were closed afterward; the test port was closed.
VS Code's Java services were left running. Available RAM after cleanup was about 12 GiB.

## Remaining stable-release gates

Run [the singleplayer acceptance checklist](RELEASE_CHECKLIST.md), including all four choices, Escape/cancellation,
Mod Menu on/off, client play/save/reopen, and the corresponding NeoForge client cases.
Server verification does not complete those items. Other advertised Minecraft versions are not newly certified by
these two version checks. Source pushes, platform publication, source-link changes, and fork archival are maintainer
steps, not automated-test outcomes.
