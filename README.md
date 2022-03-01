# MixinBooter
Allows any mixins that work on mods to work effortlessly. With a single class and an annotation. On 1.12.2.

### For Developers:

1. Add CleanroomMC's repository and query for MixinBooter's maven entry:

```
repositories {
    maven {
        url "https://maven.cleanroommc.com"
    }
}

dependencies {
    deobfCompile ("zone.rong:mixinbooter:4.1")
}
```

2. Copy over zone.rong.mixinbooter.MixinLoader.java (annotation class) to your own workspace or compile MixinBooter as a lib.
3. Annotate a class with zone.rong.mixinbooter.MixinLoader annotation. Anything in the constructor of this class will be instantiated at the appropriate time. 

Note 1: Still load any of your vanilla, forge mixins within your IFMLLoadingPlugin implementation.

Note 2: No need to call MixinBootstrap.init()


