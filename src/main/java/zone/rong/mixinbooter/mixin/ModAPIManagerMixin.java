package zone.rong.mixinbooter.mixin;

import net.minecraftforge.fml.common.ModAPIManager;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.discovery.ModDiscoverer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zone.rong.mixinbooter.decorator.FMLContextQuery;

@Mixin(value = ModAPIManager.class, remap = false)
public class ModAPIManagerMixin {

    @Inject(method = "manageAPI", at = @At("HEAD"))
    private void setASMDataTable(ModClassLoader modClassLoader, ModDiscoverer discoverer, CallbackInfo ci) {
        new FMLContextQuery(discoverer.getASMTable());
    }

}
