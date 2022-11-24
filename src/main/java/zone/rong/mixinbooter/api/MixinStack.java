package zone.rong.mixinbooter.api;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import zone.rong.mixinbooter.MixinBooterPlugin;

import java.lang.reflect.Method;
import java.util.Set;

public class MixinStack {

    public static String findMixinsInStackTrace(Throwable throwable) {
        StringBuilder mixinMetadataBuilder = new StringBuilder("\n(MixinBooter) Mixins in Stacktrace:");
        StackTraceElement[] stacktrace = throwable.getStackTrace();
        if (stacktrace.length == 0) {
            mixinMetadataBuilder.append("\nFailed to find Mixin Metadata because Stacktrace is empty\n");
            return mixinMetadataBuilder.toString();
        }

        try {
            Set<String> classes = new ObjectOpenHashSet<>();
            for (StackTraceElement stackTraceElement : stacktrace) {
                classes.add(stackTraceElement.getClassName());
            }
            Method classInfo$getMixins;
            try {
                classInfo$getMixins = ClassInfo.class.getDeclaredMethod("getMixins");
                classInfo$getMixins.setAccessible(true);

                for (String className : classes) {
                    ClassInfo classInfo = ClassInfo.fromCache(className);
                    if (classInfo != null) {
                        @SuppressWarnings("unchecked")
                        Set<IMixinInfo> mixinInfos = (Set<IMixinInfo>) classInfo$getMixins.invoke(classInfo);
                        if (!mixinInfos.isEmpty()) {
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
            } catch (ReflectiveOperationException e) {
                mixinMetadataBuilder.append("\nNot able to reflect ClassInfo#getMixins\n");
                MixinBooterPlugin.LOGGER.warn("Not able to reflect ClassInfo#getMixins");
            }

            if (mixinMetadataBuilder.length() == 36) {
                mixinMetadataBuilder.append("\nNo Mixin Metadata is found in the Stacktrace.\n");
            }

        } catch (Throwable t) {
            mixinMetadataBuilder.append("\nFailed to find Mixin Metadata in Stacktrace:\n");
            mixinMetadataBuilder.append(t);
        }

        return mixinMetadataBuilder.toString();
    }

}
