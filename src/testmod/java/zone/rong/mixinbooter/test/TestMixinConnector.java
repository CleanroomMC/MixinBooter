package zone.rong.mixinbooter.test;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;
import zone.rong.mixinbooter.Tags;

public class TestMixinConnector implements IMixinConnector {

    @Override
    public void connect() {
        LogManager.getLogger(Tags.MOD_NAME + "|MixinConnectorTest").info("Success.");
        Mixins.addConfiguration("mixin.connector_test.json");
        Mixins.addConfiguration("mixin.connector_preinit_test.json");
        Mixins.addConfiguration("mixin.connector_init_test.json");
        Mixins.addConfiguration("mixin.connector_default_test.json");
    }

}
