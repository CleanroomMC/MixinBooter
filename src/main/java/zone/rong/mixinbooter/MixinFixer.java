package zone.rong.mixinbooter;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.AbstractList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class MixinFixer {

    static Unsafe unsafe;
    static Set<String> queuedLateMixinConfigs = new ObjectOpenHashSet<>();

    /**
     * For internal usage
     */
    public static Set<String> retrieveLateMixinConfigs() {
        Set<String> ret = queuedLateMixinConfigs;
        queuedLateMixinConfigs = null;
        return ret;
    }

    static void patchAncientModMixinsLoadingMethod() {
        ClassInfo.registerCallback(ci -> {
            if (!ci.isMixin() && "net/minecraftforge/fml/common/Loader".equals(ci.getName())) {
                try {
                    // OpenJ9 Compatibility
                    Field unsafe$theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    unsafe$theUnsafe.setAccessible(true);
                    unsafe = (Unsafe) unsafe$theUnsafe.get(null);
                    Field classInfo$mixinsField = ClassInfo.class.getDeclaredField("mixins");
                    classInfo$mixinsField.setAccessible(true);
                    unsafe.putObject(ci, unsafe.objectFieldOffset(classInfo$mixinsField), new NotifiableMixinSet());
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Unable to patch for compatibility with older mixin mods", e);
                }
            }
        });
    }

    private static class NotifiableMixinSet extends HashSet<IMixinInfo> {

        private static Field mixinInfo$targetClassNames;
        long mixinInfo$targetClassNames$offset = 0L;

        @Override
        public boolean add(IMixinInfo mixinInfo) {
            if (mixinInfo$targetClassNames == null) {
                try {
                    mixinInfo$targetClassNames = mixinInfo.getClass().getDeclaredField("targetClassNames");
                    mixinInfo$targetClassNames.setAccessible(true);
                    mixinInfo$targetClassNames$offset = unsafe.objectFieldOffset(mixinInfo$targetClassNames);
                } catch (NoSuchFieldException e) {
                    throw new RuntimeException("Unable to patch for compatibility with older mixin mods", e);
                }
            }
            switch (mixinInfo.getConfig().getName()) {
                // Integrated Proxy compatibility
                case "mixins.integrated_proxy.loader.json":
                    MixinFixer.queuedLateMixinConfigs.add("mixins.integrated_proxy.mod.json");
                    unsafe.putObject(mixinInfo, mixinInfo$targetClassNames$offset, new EmptyAbsorbingList());
                    return true;
                // Just Enough IDs/Roughly Enough IDs compatibility
                case "mixins.jeid.init.json":
                    MixinFixer.queuedLateMixinConfigs.add("mixins.jeid.modsupport.json");
                    MixinFixer.queuedLateMixinConfigs.add("mixins.jeid.twilightforest.json");
                    unsafe.putObject(mixinInfo, mixinInfo$targetClassNames$offset, new EmptyAbsorbingList());
                    return true;
                // DJ2 Addons compatibility
                case "mixins.dj2addons.init.json":
                    MixinFixer.queuedLateMixinConfigs.add("mixins.dj2addons.json");
                    unsafe.putObject(mixinInfo, mixinInfo$targetClassNames$offset, new EmptyAbsorbingList());
                    return true;
                // ErebusFix compatibility
                case "mixins.loader.json":
                    if ("noobanidus.mods.erebusfix.mixins.".equals(mixinInfo.getConfig().getMixinPackage())) {
                        MixinFixer.queuedLateMixinConfigs.add("mixins.erebusfix.json");
                        unsafe.putObject(mixinInfo, mixinInfo$targetClassNames$offset, new EmptyAbsorbingList());
                        return true;
                    }
            }
            return super.add(mixinInfo);
        }

    }

    private static class EmptyAbsorbingList extends AbstractList<String> {

        @Override
        public boolean addAll(Collection<? extends String> c) {
            return true;
        }

        @Override
        public String get(int index) {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }

    }

}
