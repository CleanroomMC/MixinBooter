package zone.rong.mixinbooter.test;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;
import zone.rong.mixinbooter.Tags;
import zone.rong.mixinbooter.util.ConditionalMixin;

public class TestMixinConnector implements IMixinConnector {

    @Override
    public void connect() {
        LogManager.getLogger(Tags.MOD_NAME + "|MixinConnectorTest").info("Success.");
        Mixins.addConfiguration("mixin.connector_test.json");
        Mixins.addConfiguration("mixin.connector_preinit_test.json");
        Mixins.addConfiguration("mixin.connector_init_test.json");
        Mixins.addConfiguration("mixin.connector_default_test.json");
        ConditionalMixin.of(true, "mixin.conditional_test.json")
                .requires("jei").version("[4.0,)")
                .discourages("missingmod")
                .register();
        // mixin.conditional_disabled_test.json does not exist, so adding it shows up as a missing config in the log
        ConditionalMixin.of("mixin.conditional_disabled_test.json")
                .conflicts("jei").because("Expected to be disabled")
                .register();
    }

}
