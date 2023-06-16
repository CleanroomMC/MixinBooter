# MixinBooter
### Allows any mixins that work on mods to work effortlessly on 1.8 - 1.12.2

- Current Mixin Version: [UniMix 0.12.2 (0.8.5 branch by LegacyModdingMC)](https://github.com/LegacyModdingMC/UniMix)

- Current MixinExtra Version: [0.2.0-beta.8](https://github.com/LlamaLad7/MixinExtras)

### For Developers:

- Add CleanroomMC's repository and depend on MixinBooter's maven entry:

```
repositories {
    maven {
        url 'https://maven.cleanroommc.com'
    }
}

dependencies {
    compile 'zone.rong:mixinbooter:8.3'
}
```

### Pseudo-Changelog:

- As of 4.2, MixinBooter's API has changed and ***all mods*** that uses mixins are encouraged to depend on MixinBooter, even those that mixin into vanilla/forge/library classes. To avoid mixin version mismatches with mods crashing trying to implement modded mixins (looking at you VanillaFix). Thanks to [@embeddedt](https://github.com/embeddedt) recommending and helping me introduce this change!

- As of 5.0, [MixinExtras by @LlamaLad7](https://github.com/LlamaLad7/MixinExtras) is shaded. Available for developers to use.

- As of 8.0, MixinBooter will now work from 1.8 - 1.12.2. One single build works with all these versions! (TODO: LiteLoader support?)

### Tidbits:

- Consult `IEarlyMixinLoader` for mixins that affects vanilla, forge, or any classes that is passed to the classloader extremely early (e.g. Guava).
- Consult `ILateMixinLoader` for mixins that affects mods.
- `@MixinLoader` annotation is, as of 4.2, deprecated. The functionality is akin to `ILateMixinLoader`.
