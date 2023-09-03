package zone.rong.mixinbooter.mixin;

import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.launch.platform.GlobalMixinContextQuery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import zone.rong.mixinbooter.MixinBooterPlugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

/**
 * Mixin that allows CrashReports to be appended with mixin information.
 * Any classes that have mixins enacted to it within the stacktrace will be shown just after the stacktrace section.
 */
@Mixin(CrashReport.class)
public class CrashReportMixin {

    @Inject(method = "getCauseStackTraceOrString", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void afterStackTracePopulation(CallbackInfoReturnable<String> cir, StringWriter stringwriter, PrintWriter printwriter, Throwable throwable) {
        boolean hasErrored = false;
        StringBuilder mixinMetadataBuilder = null;
        while (throwable != null) {
            StackTraceElement[] stacktrace = throwable.getStackTrace();
            if (stacktrace.length > 0) {
                try {
                    Set<String> classes = new HashSet<>();
                    for (StackTraceElement stackTraceElement : stacktrace) {
                        classes.add(stackTraceElement.getClassName().replace('.', '/'));
                    }
                    Field classInfo$mixins;
                    try {
                        classInfo$mixins = ClassInfo.class.getDeclaredField("mixins");
                        classInfo$mixins.setAccessible(true);
                        for (String className : classes) {
                            ClassInfo classInfo = ClassInfo.fromCache(className);
                            while (classInfo != null) {
                                mixinMetadataBuilder = findAndAddMixinMetadata(classInfo$mixins, mixinMetadataBuilder, className, classInfo);
                                className = classInfo.getSuperName();
                                if (className == null || className.isEmpty() || "java/lang/Object".equals(className)) {
                                    break;
                                }
                                classInfo = classInfo.getSuperClass();
                            }
                        }
                    } catch (ReflectiveOperationException e) {
                        MixinBooterPlugin.LOGGER.warn("Not able to reflect ClassInfo#getMixins", e);
                        throw e;
                    }
                } catch (Throwable t) {
                    cir.setReturnValue(cir.getReturnValue() + "\nFailed to find Mixin Metadata in Stacktrace:\n" + t);
                    hasErrored = true;
                } finally {
                    throwable = throwable.getCause();
                }
            } else {
                break;
            }
        }
        if (!hasErrored) {
            if (mixinMetadataBuilder == null) {
                cir.setReturnValue(cir.getReturnValue() + "\nNo Mixin Metadata is found in the Stacktrace.\n");
            } else {
                cir.setReturnValue(cir.getReturnValue() + mixinMetadataBuilder);
            }
        }
    }

    private StringBuilder findAndAddMixinMetadata(Field classInfo$mixins, StringBuilder mixinMetadataBuilder, String className, ClassInfo classInfo) throws IllegalAccessException {
        @SuppressWarnings("unchecked")
        Set<IMixinInfo> mixinInfos = (Set<IMixinInfo>) classInfo$mixins.get(classInfo);
        if (!mixinInfos.isEmpty()) {
            if (mixinMetadataBuilder == null) {
                mixinMetadataBuilder = new StringBuilder("\n(MixinBooter) Mixins in Stacktrace:");
            }
            mixinMetadataBuilder.append("\n\t");
            mixinMetadataBuilder.append(className);
            mixinMetadataBuilder.append(':');
            for (IMixinInfo mixinInfo : mixinInfos) {
                mixinMetadataBuilder.append("\n\t\t");
                mixinMetadataBuilder.append(mixinInfo.getClassName());
                mixinMetadataBuilder.append(" (");
                mixinMetadataBuilder.append(mixinInfo.getConfig());
                mixinMetadataBuilder.append(") [");
                mixinMetadataBuilder.append(GlobalMixinContextQuery.location(mixinInfo));
                mixinMetadataBuilder.append("]");
            }
        }
        return mixinMetadataBuilder;
    }

}
