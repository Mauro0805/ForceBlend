# Paper plugin (planned)

This directory reserves a place for a future Paper adapter. There is no plugin implementation, build, supported
Paper version, or distributable JAR. It is not included in the root build and is not part of the 1.2.0 release.

## Before implementation

- [ ] Select and document the target Paper and Java versions.
- [ ] Verify a supported lifecycle hook before any world or region is opened. Do not process a loaded world.
- [ ] Review Paper's world paths and Overworld boundaries before reusing the shared preparation code.
- [ ] Reuse the once/every-opening modes, mandatory server backups, completion markers, and failure handling.
- [ ] Choose a plugin configuration format and map it explicitly to the shared settings.
- [ ] Add plugin metadata, a separate build, license packaging, and dependency isolation.
- [ ] Test startup ordering, backup restoration, failures, repeat starts, and terrain blending on disposable servers.

Until those checks pass, use the supported Fabric or NeoForge build instead. The shared core lives in
[`common/`](../common/README.md); it must not acquire Paper dependencies.
