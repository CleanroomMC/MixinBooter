package zone.rong.mixinbooter;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MixinFixer {

    static Set<String> queuedLateMixinConfigs = new ObjectOpenHashSet<>();

    /**
     * For internal usage
     */
    public static Set<String> retrieveLateMixinConfigs() {
        Set<String> ret = queuedLateMixinConfigs;
        queuedLateMixinConfigs = null;
        return ret;
    }

    /**
     * Replaces MixinInfo's caches map with something slightly more efficient
     * We can do more with this in the future.
     * This is also done for notification & interception's sake.
     * Using Unsafe here to keep compatibility with OpenJ9.
     */
    static void patchClassInfoCache() {
        try {
            Field unsafe$theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            unsafe$theUnsafe.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafe$theUnsafe.get(null);
            ClassInfo.fromCache(""); // Initialize ClassInfo before reflective accessing it
            Field classInfo$cache = ClassInfo.class.getDeclaredField("cache");
            long classInfo$cache$offset = unsafe.staticFieldOffset(classInfo$cache);
            Map<String, ClassInfo> cache = (Map<String, ClassInfo>) unsafe.getObject(ClassInfo.class, classInfo$cache$offset);
            unsafe.putObject(ClassInfo.class, classInfo$cache$offset, new NotifiableCache(cache));
        } catch (ReflectiveOperationException e) {
            MixinBooterPlugin.LOGGER.fatal("Not able to reflect ClassInfo::cache", e);
        }
    }

    private static class NotifiableCache extends Object2ObjectOpenHashMap<String, ClassInfo> {

        private NotifiableCache(Map<String, ClassInfo> existingCache) {
            super(existingCache);
        }

        @Override
        public ClassInfo put(String s, ClassInfo classInfo) {
            if ("net/minecraftforge/fml/common/Loader".equals(s)) {
                try {
                    Field classInfo$mixins = ClassInfo.class.getDeclaredField("mixins");
                    classInfo$mixins.setAccessible(true);
                    Set<IMixinInfo> mixins = (Set<IMixinInfo>) classInfo$mixins.get(classInfo);
                    classInfo$mixins.set(classInfo, new NotifiableMixinSet(mixins));
                } catch (ReflectiveOperationException e) {
                    MixinBooterPlugin.LOGGER.fatal("Not able to reflect ClassInfo::mixins to establish an interception for Loader mixins", e);
                }
            }
            return super.put(s, classInfo);
        }

    }

    private static class NotifiableMixinSet extends HashSet<IMixinInfo> {

        private NotifiableMixinSet(Set<IMixinInfo> existingSet) {
            super(existingSet);
        }

        @Override
        public boolean add(IMixinInfo mixinInfo) {
            switch (mixinInfo.getConfig().getName()) {
                // Integrated Proxy compatibility
                case "mixins.integrated_proxy.loader.json":
                    MixinFixer.queuedLateMixinConfigs.add("mixins.integrated_proxy.mod.json");
                    return true;
                // Just Enough IDs/Roughly Enough IDs compatibility
                case "mixins.jeid.init.json":
                    MixinFixer.queuedLateMixinConfigs.add("mixins.jeid.modsupport.json");
                    MixinFixer.queuedLateMixinConfigs.add("mixins.jeid.twilightforest.json");
                    return true;
                // DJ2 Addons compatibility
                case "mixins.dj2addons.init.json":
                    MixinFixer.queuedLateMixinConfigs.add("mixins.dj2addons.json");
                    return true;
            }
            return super.add(mixinInfo);
        }

    }

}
