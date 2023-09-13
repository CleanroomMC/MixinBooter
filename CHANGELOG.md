# Changelog

## [8.4] - 2023-08-12

### Changed
- Allows the entire exception chain to be inspected during mixin metadata search
- Eliminated lots of Reflection, uses Unsafe in some areas

### Fixed
- Fixed compatibility with Forge's interface annotation not being respected
- Fixed crash with SpongeForge
- Fixed duplicated mixin metadata being printed
- Fixed majority of mod incompatibility, properly