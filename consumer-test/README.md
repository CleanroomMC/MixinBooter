# consumer-test

A **standalone** mod that consumes *MixinBooter* as a real maven artifact
(`zone.rong:mixinbooter:xx.y` from `mavenLocal`)

Its purpose is to verify MixinBooter behaves correctly when it is a *dependency jar* in a foreign environment.
The situation every real consumer is in, and the one MixinBooter's in-tree `testmod` (which runs MixinBooter on the classpath) cannot reproduce.

It is **not** a subproject of MixinBooter and is not *included* by the parent.

## Prerequisites

- Build + publish the MixinBooter artifact this project consumes by running `./gradlew publishToMavenLocal` in the root project.
   - This installs `zone.rong:mixinbooter:xx.y` (the `!jar`, carrying the `MixinConfigs` manifest attribute) into local maven.
   - Re-run it whenever MixinBooter is changed.

## Pipelines

| Mechanism                           | Registered by                                                | Config → target                       | Fires in                |
|-------------------------------------|--------------------------------------------------------------|---------------------------------------|-------------------------|
| `IEarlyMixinLoader`                 | `ConsumerCoreMod` (coremod)                                  | `mixin.early.json` -> `Minecraft`     | dev + obf               |
| `ILateMixinLoader`                  | `ConsumerLateMixinLoader` (scanned by Forge)                 | `mixin.late.json` -> HEI              | dev + obf               |
| `MixinConfigs` manifest attribute   | jar's manifest (scanned by MixinBooter & CleanMix)           | `mixin.manifest.json` -> `Minecraft`  | **real jar only** (obf) |
| `MixinConnector` manifest attribute | `ConsumerMixinConnector` (scanned by MixinBooter & CleanMix) | `mixin.connector.json` -> `Minecraft` | **real jar only** (obf) |

Pipelines w/ manifest attributes require this mod to be a real jar w/ a manifest. They only fire under `runObfClient` (where the mod is
reobfuscated into a jar).

## Running

```
# Dev Client
./gradlew runClient

# Obfuscated Client (similar/if not same as a normal non-dev scenario)
./gradlew runObfClient
```

## What to look for

Each mixin logs a one line `Success.` marker on apply:

```
[Consumer Test|EarlyMixin] Success.
[Consumer Test|LateMixin] Success.
[Consumer Test|ConnectorMixin] Success.
[Consumer Test|ManifestMixin] Success.
```
