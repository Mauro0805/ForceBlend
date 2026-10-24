# 1.2.0 maintainer handoff

Original mod by Mauro0805. Maintained by Mauro0805 and SasnycoN.

Pushing the development branch and publishing a stable release are different steps. The local code may be reviewed
and pushed while runtime checks remain open. Do not describe an unchecked case as verified or publish it as stable.
No release, upload, account change, or fork archival is performed by this checklist.

## Before committing and pushing

- [ ] Review all changes on branch `1.2.0`, including moved files under `common/`, `fabric/`, and `neoforge/`.
- [ ] Run `./gradlew build` with JDK 25. Inspect the shared test report and both loader JARs.
- [ ] Review `git status --short` and `git diff --check`. Include new source directories, not only deleted old paths.
- [ ] Keep build output, local worlds, backups, IDE settings, and `.team/` out of the code commit.
- [ ] Commit and push the code branch as the maintainer.
- [ ] Separately review, commit, and push `forceblend-team`. It is a different repository.

## Singleplayer acceptance

Use disposable world copies and record game, loader, Java, ForceBlend JAR hash, and optional Mod Menu version.
Run on Fabric 1.18.2, 26.1.2, 26.2, and 26.3, and NeoForge 26.3 where applicable.

- [ ] Select an unprepared existing world: analysis reports chunks, regions, deferred work, and an ETA.
- [ ] Cancel and Escape return safely without opening or changing world data.
- [ ] Open without changes opens the world without preparation, a new backup, or a completion marker.
- [ ] Back up and prepare world is the first button and creates a restorable ZIP outside the world before terrain writes.
- [ ] Prepare without backup (risky) prepares terrain and opens the world; test with automatic backup disabled.
- [ ] With automatic backup on (the default), only Prepare world (backup is on) is offered and it backs up.
- [ ] Progress remains responsive and shows work counts, active regions, ETA, and completion.
- [ ] Generate new terrain beside the prepared border, inspect blending, play, save, quit, and reopen.
- [ ] Once mode skips the completed world; deferred runs do not prematurely mark completion.
- [ ] With Mod Menu installed on Fabric, open settings, change/save values, reopen settings, and verify persistence.
- [ ] Without Mod Menu, the mod still loads and properties-file settings work. NeoForge must not require Mod Menu.

## Legacy migration and repeated work

The dedicated-server cases for actual legacy migration, every-opening deletion, and chunk-limited continuation are
recorded in [the runtime report](RUNTIME_VERIFICATION_1.2.0.md). The checks below still require client acceptance;
do not repeat them on the only copy of a valuable world.

- [ ] Copy a world actually processed by a released ForceBlend Once build, retaining its original marker and region data.
- [ ] In once mode, confirm legacy-marker skipping and no new backup or replacement marker.
- [ ] Compare terrain bytes at the preparation boundary, before Minecraft can save unrelated runtime changes.
- [ ] In every-opening mode, retain the marker, delete a disposable terrain area while stopped, and confirm new preparation.
- [ ] With a small chunk limit, repeat starts until deferred work reaches zero, then confirm once-mode skipping.

Synthetic legacy-marker unit tests are not a substitute for the actual released-mod world case.
Dedicated-server save/restart tests are not a substitute for client play/save/reopen.

## Publication, after gameplay approval

- [ ] Publish a GitHub release from the reviewed commit and attach the ordinary Fabric and NeoForge JARs.
- [ ] Use the existing CurseForge project, ID `1746239`; preserve its slug and modpack references.
- [ ] Set the Source link to `https://github.com/Mauro0805/ForceBlend`.
- [ ] Keep original author credit, both maintainers, MIT licensing, and the agreed account/reward settings.
- [ ] Use the updated description in [PUBLISHING.md](PUBLISHING.md); tag only versions supported by recorded evidence.
- [ ] Download the uploaded files, compare checksums, and then update modpacks.
- [ ] Archive `valcheffnet/forceblend-once` only after its changes are preserved and the canonical source is live.
      First update its README with the canonical repository and replacement release; preserve its historical code.

If a preview is intentionally published before acceptance is complete, label the platform file Beta and list the
remaining checks. The artifact version remains `1.2.0`; this handoff does not introduce `1.2.1`.
