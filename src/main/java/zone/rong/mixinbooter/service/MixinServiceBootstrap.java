package zone.rong.mixinbooter.service;

import org.spongepowered.asm.service.IMixinServiceBootstrap;
import zone.rong.mixinbooter.Tags;

public class MixinServiceBootstrap implements IMixinServiceBootstrap {

    private static final String OWN_SERVICE = "zone.rong.mixinbooter.service.MixinBooterService";

    @Override
    public String getName() {
        return Tags.MOD_NAME;
    }

    @Override
    public String getServiceClassName() {
        return OWN_SERVICE;
    }

    @Override
    public void bootstrap() { }

}
