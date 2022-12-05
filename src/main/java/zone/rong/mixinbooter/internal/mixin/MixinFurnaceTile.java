package zone.rong.mixinbooter.internal.mixin;

import net.minecraft.tileentity.TileEntityFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zone.rong.mixinbooter.annotations.MixinMessage;

@Mixin(TileEntityFurnace.class)
@MixinMessage("Class mixin message")
public class MixinFurnaceTile {

    @Inject(method = "update",at = @At("HEAD"))
    @MixinMessage("Annotation test for furnace update method")
    public void update(CallbackInfo ci) {
        throw new RuntimeException("test");
    }

    @Inject(method = "isBurning()Z",at = @At("HEAD"))
    @MixinMessage("Annotation test for furnace isBurning method")
    public void isBuring(CallbackInfoReturnable<Boolean> cir){}
}
