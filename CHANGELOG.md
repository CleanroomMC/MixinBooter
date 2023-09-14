# Changelog

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