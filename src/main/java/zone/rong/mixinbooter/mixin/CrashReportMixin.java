package zone.rong.mixinbooter.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.service.MixinService;
import zone.rong.mixinbooter.Tags;

import java.util.*;

/**
 * Mixin that allows CrashReports to be appended with mixin information.
 * Any classes that have mixins enacted to it within the stacktrace will be shown just after the stacktrace section.
 */
@Mixin(CrashReport.class)
public class CrashReportMixin {

    @Inject(method = "getCauseStackTraceOrString", at = @At("RETURN"), cancellable = true)
    private void afterStackTracePopulation(CallbackInfoReturnable<String> cir, @Local(ordinal = 0) Throwable throwable) {
        Map<String, ClassInfo> classes = new LinkedHashMap<>();
        while (throwable != null) {
            if (throwable instanceof NoClassDefFoundError) {
                ClassInfo classInfo = ClassInfo.fromCache(throwable.getMessage());
                if (classInfo != null) {
                    classes.put(throwable.getMessage(), classInfo);
                }
            }
            StackTraceElement[] stacktrace = throwable.getStackTrace();
            for (StackTraceElement stackTraceElement : stacktrace) {
                String className = stackTraceElement.getClassName().replace('.', '/');
                if (classes.containsKey(className)) {
                    ClassInfo classInfo = ClassInfo.fromCache(className);
                    while (classInfo != null) {
                        classes.put(className, classInfo);
                        className = classInfo.getSuperName();
                        if (className == null || className.isEmpty() || "java/lang/Object".equals(className)) {
                            break;
                        }
                        classInfo = classInfo.getSuperClass();
                    }
                }
            }
            throwable = throwable.getCause();
        }
        if (classes.isEmpty()) {
            cir.setReturnValue(cir.getReturnValue() + "\nNo Mixin Metadata is found in the Stacktrace.\n");
        } else {
            StringBuilder mixinMetadataBuilder = new StringBuilder("\nMixins in Stacktrace:");
            boolean addedMetadata = false;
            for (Map.Entry<String, ClassInfo> entry : classes.entrySet()) {
                addedMetadata |= mixinbooter$findAndAddMixinMetadata(mixinMetadataBuilder, entry.getKey(), entry.getValue());
            }
            if (addedMetadata) {
                cir.setReturnValue(cir.getReturnValue() + mixinMetadataBuilder);
            } else {
                cir.setReturnValue(cir.getReturnValue() + "\nNo Mixin Metadata is found in the Stacktrace.\n");
            }
        }
    }

    @Unique
    private boolean mixinbooter$findAndAddMixinMetadata(StringBuilder mixinMetadataBuilder, String className, ClassInfo classInfo) {
        Set<IMixinInfo> mixinInfos = classInfo.getMixins();
        if (!mixinInfos.isEmpty()) {
            mixinMetadataBuilder.append("\n\t");
            mixinMetadataBuilder.append(className);
            mixinMetadataBuilder.append(':');
            for (IMixinInfo mixinInfo : mixinInfos) {
                mixinMetadataBuilder.append("\n\t\t");
                mixinMetadataBuilder.append(mixinInfo.getClassName());
                mixinMetadataBuilder.append(" (");
                mixinMetadataBuilder.append(mixinInfo.getConfig());
                mixinMetadataBuilder.append(") [");
                mixinMetadataBuilder.append(mixinInfo.getConfig().getCleanSourceId());
                mixinMetadataBuilder.append("]");
            }
            return true;
        }
        return false;
    }

}
