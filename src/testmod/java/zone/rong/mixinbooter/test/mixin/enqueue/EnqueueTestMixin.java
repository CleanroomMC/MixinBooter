package zone.rong.mixinbooter.test.mixin.enqueue;

import mezz.jei.JustEnoughItems;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.rong.mixinbooter.Tags;

@Mixin(value = JustEnoughItems.class, remap = false)
public class EnqueueTestMixin {

    @Inject(method = "<init>", at = @At("HEAD"), remap = false)
    private static void onInit(CallbackInfo ci) {
        LogManager.getLogger(Tags.MOD_NAME + "|EnqueueTestMixin").info("Success.");
    }

}
