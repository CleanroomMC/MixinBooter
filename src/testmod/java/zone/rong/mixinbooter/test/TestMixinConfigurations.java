package zone.rong.mixinbooter.test;

import net.minecraftforge.common.config.Config;
import zone.rong.mixinbooter.util.MixinConfiguration;
import zone.rong.mixinbooter.util.MixinConfiguration.Dependency;

// mixin.configuration_disabled_test.json does not exist, so any attempt to add it shows up as a missing config in the log
@Config(modid = TestMod.MOD_ID, name = TestMod.MOD_ID + "_configurations")
public class TestMixinConfigurations {

    @MixinConfiguration(name = "mixin.configuration_test.json", depends = @Dependency(modid = "jei", version = "[4.0,)", crash = false))
    public static boolean configurationTest = true;

    @MixinConfiguration(name = "mixin.configuration_disabled_test.json")
    public static boolean configurationOffTest = false;

    @MixinConfiguration(name = "mixin.configuration_disabled_test.json", depends = @Dependency(modid = "missingmod", crash = false, reason = "Expected to be disabled"))
    public static boolean configurationMissingTest = true;

}
