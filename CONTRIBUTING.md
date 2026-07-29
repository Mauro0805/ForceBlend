# Contributing to ForceBlend

ForceBlend is a community-oriented proof of concept. Bug fixes, compatibility reports, tests, and documentation
improvements are welcome. Pull requests may be reviewed only occasionally, and submission does not guarantee review,
merge, or a future release. If you want to move faster, maintaining a fork is encouraged.

Because the mod edits world data, focused changes with clear safety evidence are much easier to review than broad
rewrites.

## Before changing code

- Read the [project scope](docs/PROJECT_SCOPE.md) and [architecture](docs/ARCHITECTURE.md).
- Search existing issues and pull requests.
- Never test against the only copy of a valuable world.
- Keep world-data behavior conservative, deterministic, and easy to audit.
- Separate confirmed behavior from assumptions in issues, comments, and documentation.

## Build and test

The workspace uses JDK 25 to compile the Minecraft 26.2 adapter while producing Java 17 mod bytecode. Use the included
Gradle wrapper; a system Gradle installation is not required.

```powershell
./gradlew.bat clean build
```

Use `./gradlew` on Linux or macOS.

For region-processing changes, also run the copy test against a disposable real world:

```powershell
./gradlew.bat forceBlendWorldCopyTest "-PforceblendWorld=C:\path\to\world" "-PforceblendMinecraftVersion=1.18.2"
```

The task copies the region directory to a temporary location and must never modify the supplied source.

Automated tests are necessary but not the same as an in-game verification. Report the exact Minecraft, Fabric Loader,
Java, operating-system, and mod versions used for runtime testing. At present, stable 1.0.0 is verified in game on
1.18.2 and 26.2; 1.1.0-beta.1 awaits a fresh in-game test on both.

## Code boundaries

- Use English for code, comments, logs, commits, issues, pull requests, and documentation.
- Keep Minecraft and Fabric lifecycle code in the client source set.
- Keep the shared processing, configuration, and backup code free of Minecraft classes and compatible with Java 17.
- Keep chunk NBT mutation inside `ChunkModifier`.
- Preserve the local-singleplayer and Overworld-only boundary unless a fork intentionally defines a different product.
- Keep pre-analysis read-only. A user decision must happen before backup or world modification begins.
- Reuse the immutable `ProcessingPlan`; do not add a second full scan between confirmation and application.
- Preserve stale-plan rejection before parallel writes.
- Never let two workers write the same region file.
- Keep configuration and region writes atomic where the filesystem supports it, with a safe fallback and cleanup.
- Preserve every compression format and external-chunk behavior already covered by tests.
- Add or update tests for behavioral changes, especially cancellation, limits, concurrency, and failure cleanup.

The desktop UI intentionally avoids Minecraft screen classes so the same client code can load across the declared
version range. Mod Menu integration must remain optional and must not become a hard compile-time or runtime dependency.

## Documentation and release claims

Do not describe a Minecraft version as verified unless a complete in-game test was recorded. Do not publish speedup or
benchmark claims without a reproducible setup and results. Pre-release notes must remain explicit about what passed
automated tests and what still needs runtime testing.

## Pull requests

A useful pull request includes:

- one focused change;
- a short explanation of the user-visible effect and world-data risk;
- the exact environments tested;
- updated tests and documentation where relevant;
- sanitized logs or a small disposable fixture when they help reproduce the result.

Contributions are provided under the repository's [MIT License](LICENSE.txt).
