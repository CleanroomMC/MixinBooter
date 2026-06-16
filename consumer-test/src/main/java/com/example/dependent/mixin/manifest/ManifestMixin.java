package com.example.dependent.mixin.manifest;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class)
public class ManifestMixin {

    @Inject(method = "<init>", at = @At("HEAD"))
    private static void onInit(CallbackInfo ci) {
        LogManager.getLogger("Consumer Test|ManifestMixin").info("Success.");
    }

}
