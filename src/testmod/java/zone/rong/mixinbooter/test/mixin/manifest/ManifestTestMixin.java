package zone.rong.mixinbooter.test.mixin.manifest;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.rong.mixinbooter.Tags;

@Mixin(value = Minecraft.class)
public class ManifestTestMixin {

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void onInit(CallbackInfo ci) {
        LogManager.getLogger(Tags.MOD_NAME + "|ManifestTestMixin").info("Success.");
    }

}
