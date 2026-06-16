package com.example.dependent.mixin.late;

import mezz.jei.JustEnoughItems;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JustEnoughItems.class, remap = false)
public class LateMixin {

    @Inject(method = "<init>", at = @At("HEAD"), remap = false)
    private static void onInit(CallbackInfo ci) {
        LogManager.getLogger("Consumer Test|LateMixin").info("Success.");
    }

}
