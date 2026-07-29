# Contributing to ForceBlend

ForceBlend is a community-friendly proof of concept. Bug fixes, compatibility work, tests, and documentation
improvements are welcome. I may review pull requests only occasionally, so a PR can remain open or
unmerged for a long time. If you want to move faster, you are welcome to maintain a fork.

Because this mod edits world data, small and well-tested changes are much easier to review than large rewrites.

## Before changing code

- Read the [project scope](docs/PROJECT_SCOPE.md) and [architecture](docs/ARCHITECTURE.md).
- Search existing issues and pull requests.
- Never test on the only copy of a valuable world.
- Keep behavior that affects world data conservative and easy to audit.

## Build the project

You need JDK 25, Git, and internet access for the first dependency download. Use the included Gradle wrapper:

```powershell
./gradlew.bat clean build
```

Use `./gradlew` on Linux or macOS. A system Gradle installation is not needed.

## Code guidelines

- Use English for code, comments, logs, commits, issues, pull requests, and documentation.
- Keep Minecraft/Fabric lifecycle code in the client adapters and out of the processing core.
- Keep chunk NBT mutation inside `ChunkModifier`.
- Preserve the Overworld-only and local-singleplayer-only boundary unless a fork intentionally changes the product.
- Fail clearly on malformed data instead of silently skipping it.
- Preserve support for every compression format already covered by the tests.
- Add or update tests for behavioral changes.

For region-processing changes, also run the copy test against a disposable real world:

```powershell
./gradlew.bat forceBlendWorldCopyTest "-PforceblendWorld=C:\path\to\world" "-PforceblendMinecraftVersion=1.18.2"
```

The task copies the region directory to a temporary location and must never modify the supplied source.

## Pull requests

A useful pull request includes:

- one focused change;
- a short explanation of the user-visible effect and world-data risk;
- the exact Minecraft, Fabric Loader, Java, and operating-system versions tested;
- updated tests and documentation where relevant;
- sanitized logs or a small disposable fixture when they help reproduce the result.

Submitting a pull request does not guarantee review or inclusion. Contributions are provided under the repository's
[MIT License](LICENSE.txt).
