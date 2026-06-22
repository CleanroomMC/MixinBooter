# MixinBooter
### Allows any mixins that work on mods to work effortlessly on 1.8 - 1.12.2

- Current Mixin Version: [CleanMix 0.2.2 by CleanroomMC, a fork of SpongePowered/Fabric Mixin (0.8.x)](https://github.com/CleanroomMC/CleanMix)

- Current MixinExtra Version: [0.5.4](https://github.com/LlamaLad7/MixinExtras)

### Pseudo-Changelog:

- As of 4.2, MixinBooter's API has changed and ***all mods*** that uses mixins are encouraged to depend on MixinBooter, even those that mixin into vanilla/forge/library classes. To avoid mixin version mismatches with mods crashing trying to implement modded mixins (looking at you VanillaFix). Thanks to [@embeddedt](https://github.com/embeddedt) recommending and helping me introduce this change!

- As of 5.0, [MixinExtras by @LlamaLad7](https://github.com/LlamaLad7/MixinExtras) is shaded. Available for developers to use.

- As of 8.0, MixinBooter will now work from 1.8 - 1.12.2. One single build works with all these versions! (TODO: LiteLoader support?)

- As of 8.4, MixinBooter actively attempts to be compatible with [SpongeForge](https://github.com/SpongePowered/SpongeForge)

- As of 9.2, MixinBooter reinstates the older MixinLoader annotation for 1.8.x usages.

- As of 10.0, MixinBooter follows Mixin 0.8.7

- As of 11.0, MixinBooter is built on [CleanMix](https://github.com/CleanroomMC/CleanMix). As an effort to create an unified backend with [Cleanroom](https://cleanroommc.com/).
  - No longer would you need to declare dependencies for the annotation processor yourself.
  - Also adds a config file (`config/mixinbooter.cfg`) to blacklist mixin configs and toggle debug options
  - Dedicated `logs/mixinbooter.log` mixin log. With ability to trace class-loading for precise debugging.
  - Allows traditional `MixinConfig` + `MixinConnector` manifest attribute entries to be fully involved in the ecosystem
  - Mod discovery for mixin owners, better `isModLoaded` checks
  - Suppresses Forge's *corrupt zip* warnings

### For Developers ~ Getting Started:

1. Add CleanroomMC's repository and depend on MixinBooter's maven entry:
```groovy
repositories {
    maven {
        url 'https://maven.cleanroommc.com'
    }
}

dependencies {
    def mixin = 'zone.rong:mixinbooter:11.0'
    
    implementation (mixin) {
        transitive = false
    }
    annotationProcessor mixin

    // RetroFuturaGradle for refmap generation:
    modUtils.enableMixins(mixin)
    // modUtils.enableMixins(mixin, 'mod_id.mixins.refmap.json') << add refmap name as 2nd arg (optional)
}
```

2. Pick how to register your mixin configurations. MixinBooter supports lots of approaches:

(As of 11.0, early/late divide is no longer present, therefore IEarly/ILateMixinLoaders are deprecated)

- **`MixinConfigs` manifest attribute**: no loader class needed. Add a space-separated list of your mixin configuration names to your jar's manifest. MixinBooter reads it straight from the jar's manifest and registers them.
- **`MixinConnector` manifest attribute**: register configs programmatically. Point it at a class implementing `org.spongepowered.asm.mixin.connect.IMixinConnector`, its `connect()` is called during boot, where you call `Mixins.addConfiguration(...)` yourself.

Both manifest attributes are set on your jar task:

```groovy
jar {
    manifest {
        attributes(
                'MixinConfigs': 'mixins.mymod.json',
                'MixinConnector': 'com.example.mymod.MyMixinConnector'
        )
    }
}
```

```java
public class MyMixinConnector implements IMixinConnector {
    
    @Override
    public void connect() {
        Mixins.addConfiguration("mixins.mymod.json");
    }
    
}
```

