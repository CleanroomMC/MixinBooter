package zone.rong.mixinbooter.test;

import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;

// mixin.toggle_disabled_test.json does not exist, so any attempt to add it shows up as a missing config in the log
@Config(modid = TestMod.MOD_ID)
@MixinConfig(name = TestMod.MOD_ID)
public class TestMixinToggles {

    @MixinConfig.MixinToggle(mixinJson = "mixin.toggle_test.json", defaultValue = true)
    @MixinConfig.CompatHandling(modid = "jei", desired = true)
    public static boolean toggleTest = true;

    @MixinConfig.MixinToggle(mixinJson = "mixin.toggle_disabled_test.json", defaultValue = true)
    @MixinConfig.CompatHandling(modid = "mixinbooter", desired = false, reason = "Expected to be disabled")
    public static boolean toggleDisabledTest = true;

}
