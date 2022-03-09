package zone.rong.mixinbooter;

public interface IMixinLoader {

    // Purely a marker interface. Use it in classes and said classes will be instantiated.
    // Make sure there is an empty-arg constructor as LoaderMixin will call newInstance on it.
    // Feel free to do any Mixin related things in the constructor. But, most importantly, add (mod mixin) configs there.

    // This replaces @MixinLoader annotation.

}
