# Changelog

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