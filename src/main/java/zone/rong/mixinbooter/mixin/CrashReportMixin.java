package zone.rong.mixinbooter.mixin;

import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import zone.rong.mixinbooter.api.MixinStack;

import java.io.PrintWriter;
import java.io.StringWriter;

@Mixin(CrashReport.class)
public class CrashReportMixin {

    @Inject(method = "getCauseStackTraceOrString", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void afterStackTracePopulation(CallbackInfoReturnable<String> cir, StringWriter stringwriter, PrintWriter printwriter, Throwable throwable) {
        cir.setReturnValue(cir.getReturnValue() + new MixinStack(throwable));
    }

}
