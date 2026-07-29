# Security and world-data safety

ForceBlend is a proof of concept that intentionally rewrites Minecraft region files. Keep an offline backup and verify
that it can be restored before using the mod on a world you care about. Integrated backups are an extra safeguard, not
a replacement for that copy, and they are disabled by default.

## Release status

Stable **1.0.0** was verified in game on Minecraft 1.18.2 and 26.2. **1.1.0-beta.1** has passed automated tests but
awaits a new in-game test on both versions. Other versions in the declared metadata range are compatibility targets,
not guaranteed or actively supported environments.

There is no guaranteed maintenance schedule, response time, or future update. The community may fork and publish fixes
under the MIT License.

## Safety model

The 1.1 beta separates world opening into two stages:

1. A read-only analysis builds an immutable plan and shows the expected work.
2. Only an explicit apply choice can start a backup or region write.

Before applying, ForceBlend rejects a stale plan if region files were added, removed, resized, retimestamped, or changed
in ways covered by its Anvil-header and external-chunk checks. Changed regions are assembled in temporary files,
flushed, validated, and moved into place only after their processing succeeds. Unchanged regions are not replaced.
Changed external `.mcc` streams use local rollback snapshots so an ordinary move failure restores their original
sidecars and region file.

Complete ZIP backups are written as partial files outside the world, checked for source changes, and published under a
collision-safe timestamp only after completion. Cancellation and ordinary failure remove the partial archive.
Symbolic links, unsafe ZIP paths, and overlapping source/destination paths are rejected.

These controls reduce risk but cannot prevent power loss, filesystem or hardware failure, malicious native code,
third-party editors changing files at exactly the wrong time, or unknown interactions with other mods.

## Reporting a vulnerability

Use GitHub's private security advisory feature for an exploit, malicious world, path-handling issue, or behavior that
could cause data loss. Do not post private saves, credentials, or personal filesystem paths in a public issue.

A useful report includes:

- the ForceBlend, Minecraft, Fabric Loader, Java, and operating-system versions;
- whether the issue happened during analysis, backup, validation, or application;
- a minimal reproduction and its possible impact;
- sanitized logs and a disposable fixture when safe to share.

A report may not receive a quick response. Stop testing on the original world and preserve both the world and backup
copies for recovery.
