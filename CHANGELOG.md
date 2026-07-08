# Changelog

## [11.4] - 2026-07-09

### Fixed
- Rescuing all coremods that have a TweakClass attribute, instead only help those that have MixinTweaker as the TweakClass

## [11.3] - 2026-07-08

### Added
- Warnings if mixins have somehow got no accessor targets
- Exclusion of Mixin's launch package by default, may fix dev environment woes

### Fixed
- Concurrent modifications when mixin configurations are being registered and selected
- Allow `IExtension` classes to passthrough and be in the same packages as mixins
- Superclasses not being considered if they're interfaces

## [11.2] - 2026-07-05

### Fixed
- #124, now MixinExtras won't crash in older Minecraft versions
- #126, injects `CoremodRescuer` earlier in the tweakers list, helps mods using wrong APIs to still submit mixin configurations properly
- Incompatibility with mods like VintageFix which errors due to an upstream regression for synthetic inner classes

### Changed
- Updated CleanMix to 0.4.1
- Prefer reading `ForgeVersion` with reflection for the current Minecraft version (thanks to @ZZZank)

## [11.1] - 2026-07-01

### Fixed
- Compatibilities with older minecraft versions

### Changed
- To have more robust suppression of class reading errors presented by Forge

## [11.0] - 2026-06-27

### Added
- Allowing traditional `MixinConfigs` & `MixinConnector` way of registering mixins once again
- Suppression of "corrupt zip" warnings from Forge when reading mods and parsing data
- Mod discovery (properly done). Accessible via `ModDiscoverer`
- Way to resolve mod ids in a more correct and explicit manner
- Dedicated mixin logging output: `/logs/mixinbooter.log`
- Configuration for various mixin flags and audit file
- Class-loading now traceable, helps with certain mixins trying to mixin classes that are loaded too early, good way to debug this issue
- Better testing within the workspace
- Warnings of mods shading mixins into their own jar file
- Documentation

### Changed
- CleanMix! Bridged the gap, CleanMix will be used in Cleanroom (as it updates soon tm)
- Deprecated early/mixin loading stages, division is no longer present
- Deprecated loader, hijacker interfaces
- Included annotation processor dependencies for maven artifact
- Better "mod is present" checks
- Phasing out mixin "phases", configs gets immediately selected and processed

### Removed
- Explicit mod fixes

## [10.7] - 2025-09-30

### Changed
- Updated MixinExtras from 0.5.0-rc.1 to 0.5.0

## [10.6] - 2025-03-25

### Changed
- Updated MixinExtras from 0.5.0-beta.5 to 0.5.0-rc.1

### Fixed
- GsonBuilder#setLenient not existing on older Gson versions

## [10.5] - 2025-02-03

### Added
- Explicit compatibility with checking if Optifine is loaded at coremod stages (for the Context obj)
- Hotswap Agent related manifest entries to allow hotswapping outside of dev

### Fixed
- Edge-cases in reading some mods' mcmod.infos

## [10.4] - 2025-01-24

### Changed
- Made reading mod metadata quicker and simpler
- Updated MixinExtras to 0.5.0-beta.5

### Fixed
- SpongeForge compatibility, due to a Forge oversight

## [10.3] - 2025-01-14

### Added
- Context to `IMixinConfigHijacker`

### Changed
- Allowed all mixin configs to be hijacked

### Fixed
- Use `JsonReader#setLenient` over `GsonBuilder#setLenient` for older Gson versions supplied by older Minecraft versions
- Use deprecated `Handle` constructor for older ASM versions supplied by older Minecraft versions
- Catch loader issues instead of exiting and not loading subsequent loaders

## [10.2] - 2024-11-16

### Fixed
- Specified ordinality on a local capture for when the mixin is applied in brittle contexts

## [10.1] - 2024-11-04

### Fixed
- Few certain vanilla classes loading early, breaking some deobfuscation into SRG

## [10.0] - 2024-11-02

### Added
- New API (Context) for early/late loaders

### Changed
- Updated UniMix, is now updated with Fabric Mixin 0.15.3, Mixin 0.8.7
- Updated to MixinExtras 0.5.0-beta4
- Better logging at launch

### Fixed
- Hijackers not applied to late mixins
- Mod description typo

## [9.4] - 2024-09-20

### Changed
- Updated UniMix
- Enable `comformVisibility` to `true` by default - thanks to jbredwards

### Fixed
- Compatibility with older ASM libraries - thanks to HowardZHY

## [9.3] - 2024-08-18

### Changed
- Updated MixinExtras to 0.3.6

## [9.2] - 2024-08-08

### Changed
- Reinstated `@MixinLoader` annotation, primarily for 1.8.x usages as Forge does not support gathering of interfaces

### Fixed
- Mixin source files not embed within the source jar
- 1.8.x related crashes fixed (thanks to @HowardZHY!)

## [9.1] - 2024-02-03

### Changed
- Removed fastutil usages, to keep compatibility with Minecraft versions that used different fastutil versions

## [9.0] - 2024-01-30

### Added
- `IMixinConfigHijacker` API, allows denial of mixin configurations from being applied (idea: @Desoroxxx)

### Changed
- Updated MixinExtras to 0.3.5

### Fixed
- Mixin information being duplicated in crash reports in certain situations

## [8.9] - 2023-11-02

### Changed
- Updated MixinExtras from 0.2.0-beta.9 to 0.2.1-beta.2, now supports wrapping of object instantiations with `@WrapOperation`

## [8.8] - 2023-10-31

### Changed
- Logging during mod gathering

### Fixed
- Optimized mod gathering
- Fixed delegated transformers not being rebuild before late mixins are loaded. Resulting in transformers that are registered later not properly running

## [8.7] - 2023-10-30

### Fixed
- Fixed issues with different builds of DJ2Addons.

## [8.6] - 2023-09-14

### Added
- Added a dummy mcmod.info

### Changed
- Only allowing SpongeForge 7.4.8+ from loading with MixinBooter.

### Fixed
- Fixed issue with Modrinth uploads (not allowing forge mods without mcmod.info packaged to be uploaded)

## [8.5] - 2023-09-13

### Added
- Implemented FMLContextQuery, and MixinContextQuery which is extensible for different platforms

### Changed
- Better description for the mod

### Fixed
- Made SpongeForge's PrettyPrinter backwards-compatible, more fixes may follow up if bugs are found
- Compatibility with Uncrafting Blacklist

## [8.4] - 2023-08-12

### Changed
- Allows the entire exception chain to be inspected during mixin metadata search
- Eliminated lots of Reflection, uses Unsafe in some areas

### Fixed
- Fixed compatibility with Forge's interface annotation not being respected
- Fixed crash with SpongeForge
- Fixed duplicated mixin metadata being printed
- Fixed majority of mod incompatibility, properly