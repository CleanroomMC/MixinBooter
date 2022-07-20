# MixinBooter
Allows any mixins that work on mods to work effortlessly on 1.12.2.

### For Developers:

- Add CleanroomMC's repository and depend on MixinBooter's maven entry:

```
repositories {
    maven {
        url "https://maven.cleanroommc.com"
    }
}

dependencies {
    deobfCompile ("zone.rong:mixinbooter:5.0")
}
```

- As of 4.2, MixinBooter's API has changed and ***all mods*** that uses mixins are encouraged to depend on MixinBooter, even those that mixin into vanilla/forge/library classes. To avoid mixin version mismatches with mods crashing trying to implement modded mixins (looking at you VanillaFix). Thanks to [@embeddedt](https://github.com/embeddedt) recommending and helping me introduce this change!

- As of 5.0, [MixinExtras by @LlamaLad7](https://github.com/LlamaLad7/MixinExtras) is shaded. Available for developers to use.


- Consult `IEarlyMixinLoader` for mixins that affects vanilla, forge, or any classes that is passed to the classloader extremely early (e.g. Guava).
- Consult `ILateMixinLoader` for mixins that affects mods.
- `@MixinLoader` annotation is, as of 4.2, deprecated. It's functionality is akin to `ILateMixinLoader`.
