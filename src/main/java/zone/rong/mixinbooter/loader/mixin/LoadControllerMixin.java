package zone.rong.mixinbooter.loader.mixin;

import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zone.rong.mixinbooter.MixinBooterPlugin;

import java.net.MalformedURLException;
import java.util.List;

@Mixin(LoadController.class)
public class LoadControllerMixin {

    @Shadow(remap = false) private Loader loader;

    static {
        MixinBooterPlugin.LOGGER.info("LoadController loaded, due to unexplainable reasons, we cannot mixin into Loader reliably.");
    }

    @Redirect(method = "buildModList", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false), remap = false)
    private <M> boolean whileAddingMods(List<M> list, M container) throws MalformedURLException {
        this.loader.getModClassLoader().addFile(((ModContainer) container).getSource());
        return list.add(container);
    }

}
