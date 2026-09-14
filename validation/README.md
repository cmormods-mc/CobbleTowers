# CobbleTowers validation

## Notes

### Exact functionality

The CI pipeline validates CobbleTowers in two independent ways:

1. A standalone build verifies that CobbleTowers compiles and its unit tests pass against the pinned Minecraft/Fabric/Cobblemon baseline.
2. A cross-repository compatibility build checks out `cmormods-mc/SnobblemonRaids` at `feature/tower-integration-api`, builds that exact source revision, and compiles CobbleTowers against the resulting remapped runtime JAR.

The compatibility job never substitutes a previously published or cached CobbleRaids API artifact for the source revision under test; the JAR consumed by CobbleTowers is produced earlier in the same workflow job.

### Architectural role

`validate_architecture.py` enforces that CobbleTowers may reference only `com.cobbleraids.api`. Any import from CobbleRaids implementation packages fails CI immediately.

### Performance impact

All checks are build-time only. They add no runtime callbacks, tick work, entity scanning, reflection, or server memory usage.

### Validation boundary

A green CI build proves compile-time compatibility, static architecture rules, and automated tests. It does not replace dedicated-server regression testing for Cobblemon/Showdown behavior, chunk lifecycle, multiplayer disconnects, or live MSPT profiling.
