package zone.rong.mixinbooter.api;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import zone.rong.mixinbooter.MixinBooterPlugin;

import java.lang.reflect.Method;
import java.util.Set;

public class MixinStack {
    private static Method classInfo$getMixins;
    private boolean containsMixinsInStack;
    private String mixinStack;

    static {
        try {
            classInfo$getMixins = ClassInfo.class.getDeclaredMethod("getMixins");
            classInfo$getMixins.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            MixinBooterPlugin.LOGGER.error("Not able to reflect ClassInfo#getMixins");
        }
    }

    public MixinStack(Throwable throwable) {
        findMixinsInStack(throwable.getStackTrace());
    }

    public MixinStack(StackTraceElement[] stacktrace) {
        findMixinsInStack(stacktrace);
    }

    private void findMixinsInStack(StackTraceElement[] stacktrace) {
        if (classInfo$getMixins == null) {
            mixinStack = "\n(MixinBooter) Not able to reflect ClassInfo#getMixins\n";
            return;
        }

        if (stacktrace.length == 0) {
            mixinStack = "\n(MixinBooter) Failed to find Mixin Metadata because the provided Stacktrace is empty\n";
            return;
        }

        StringBuilder mixinMetadataBuilder = null;
        Set<String> classes = new ObjectOpenHashSet<>();
        for (StackTraceElement stackTraceElement : stacktrace) {
            classes.add(stackTraceElement.getClassName());
        }
        try {
            for (String className : classes) {
                ClassInfo classInfo = ClassInfo.fromCache(className);
                if (classInfo != null) {
                    @SuppressWarnings("unchecked")
                    Set<IMixinInfo> mixinInfos = (Set<IMixinInfo>) classInfo$getMixins.invoke(classInfo);
                    if (!mixinInfos.isEmpty()) {
                        if (mixinMetadataBuilder == null) {
                            mixinMetadataBuilder = new StringBuilder("\n(MixinBooter) Mixins in Stacktrace:");
                        }
                        mixinMetadataBuilder.append("\n\t");
                        mixinMetadataBuilder.append(className);
                        mixinMetadataBuilder.append(":");
                        for (IMixinInfo mixinInfo : mixinInfos) {
                            mixinMetadataBuilder.append("\n\t\t");
                            mixinMetadataBuilder.append(mixinInfo.getClassName());
                            mixinMetadataBuilder.append(" (");
                            mixinMetadataBuilder.append(mixinInfo.getConfig().getName());
                            mixinMetadataBuilder.append(")");
                        }
                    }
                }
            }

            if (mixinMetadataBuilder == null) {
                mixinStack = "\n(MixinBooter) No Mixin Metadata is found in the Stacktrace.\n";
                return;
            }

        } catch (Throwable t) {
            mixinStack = "\n(MixinBooter) Failed to find Mixin Metadata in Stacktrace: " + t + "\n";
            return;
        }

        containsMixinsInStack = true;
        mixinStack = mixinMetadataBuilder.toString();
    }

    public boolean doseStackContainMixins() {
        return containsMixinsInStack;
    }

    @Override
    public String toString() {
        return mixinStack;
    }

}
