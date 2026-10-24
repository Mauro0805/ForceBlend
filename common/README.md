# Shared sources

Both loader builds compile these sources directly into their own JARs. This directory is not a third mod artifact.

- `src/main/java/`: loader-neutral processing, configuration, backup, and completion state.
- `src/main/resources/`: shared third-party notices and licenses.
- `src/client/java/`: shared native screen bridge, world-open workflow, and settings controller.
- `src/test/java/`: shared core and client tests, run through the Fabric build. Adapter tests live in their loader tree.

Core code remains Java 17 compatible and free of loader and Minecraft classes. Shared client code uses reflection
and the loader-provided ASM runtime. Loader entrypoints, mixins, Mod Menu adapter, and metadata belong in
`fabric/` or `neoforge/`, not here.
