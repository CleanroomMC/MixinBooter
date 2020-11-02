# MixinBooter
Allows any mixins that work on mods to work effortlessly. With a single class and an annotation. On 1.12.2.

To use:

1. Copy over zone.rong.mixinbooter.MixinLoader.java (annotation class) to your own workspace or compile MixinBooter as a lib.
2. Annotate a class with zone.rong.mixinbooter.MixinLoader annotation. Anything in the constructor of this class will be instantiated at the appropriate time. 

Note 1: Still load any of your vanilla, forge mixins within your IFMLLoadingPlugin implementation.
Note 2: No need to call MixinBootstrap.init()
