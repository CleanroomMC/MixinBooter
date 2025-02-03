# MixinBooter
### Allows any mixins that work on mods to work effortlessly on 1.8 - 1.12.2

- Current Mixin Version: [UniMix 0.15.3 forked by CleanroomMC, derived from 0.8.7 branch by LegacyModdingMC](https://github.com/CleanroomMC/UniMix)

- Current MixinExtra Version: [0.5.0-beta5](https://github.com/LlamaLad7/MixinExtras)

### For Developers:

- Add CleanroomMC's repository and depend on MixinBooter's maven entry:

```groovy
repositories {
    maven {
        url 'https://maven.cleanroommc.com'
    }
}

dependencies {

    // Common:
    annotationProcessor 'org.ow2.asm:asm-debug-all:5.2'
    annotationProcessor 'com.google.guava:guava:32.1.2-jre'
    annotationProcessor 'com.google.code.gson:gson:2.8.9'

    // ForgeGradle:
    implementation ('zone.rong:mixinbooter:10.5') {
        transitive = false
    }
    annotationProcessor ('zone.rong:mixinbooter:10.5') {
        transitive = false
    }
    
    // RetroFuturaGradle:
    String mixinBooter = modUtils.enableMixins('zone.rong:mixinbooter:10.5')
    // modUtils.enableMixins('zone.rong:mixinbooter:10.5', 'mod_id.mixins.refmap.json') << add refmap name as 2nd arg (optional)
    api (mixinBooter) {
        transitive = false
    }
    annotationProcessor (mixinBooter) {
        transitive = false
    }
}
```

### Pseudo-Changelog:

- As of 4.2, MixinBooter's API has changed and ***all mods*** that uses mixins are encouraged to depend on MixinBooter, even those that mixin into vanilla/forge/library classes. To avoid mixin version mismatches with mods crashing trying to implement modded mixins (looking at you VanillaFix). Thanks to [@embeddedt](https://github.com/embeddedt) recommending and helping me introduce this change!

- As of 5.0, [MixinExtras by @LlamaLad7](https://github.com/LlamaLad7/MixinExtras) is shaded. Available for developers to use.

- As of 8.0, MixinBooter will now work from 1.8 - 1.12.2. One single build works with all these versions! (TODO: LiteLoader support?)

- As of 8.4, MixinBooter actively attempts to be compatible with [SpongeForge](https://github.com/SpongePowered/SpongeForge)

- As of 9.2, MixinBooter reinstates the older MixinLoader annotation for 1.8.x usages.

- As of 10.0, MixinBooter follows Mixin 0.8.7

### Tidbits:

- Consult `IEarlyMixinLoader` for mixins that affects vanilla, forge, or any classes that is passed to the classloader extremely early (e.g. Guava).
- Consult `ILateMixinLoader` for mixins that affects mods.
- `@MixinLoader` annotation's ~~, as of 4.2, deprecated. The~~ functionality is akin to `ILateMixinLoader`. Both can be used at the same time, especially for 1.8.x versions where it is needed.
