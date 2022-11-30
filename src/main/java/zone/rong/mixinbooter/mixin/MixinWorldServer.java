package zone.rong.mixinbooter.mixin;

import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.rong.mixinbooter.api.MixinMessage;

@Mixin(WorldServer.class)
public class MixinWorldServer {

    @Inject(method = "updateEntities",at = @At("HEAD"))
    @MixinMessage("Annotation test for furnace updateEntities method")
    public void updateEntities(CallbackInfo ci){

    }
}
