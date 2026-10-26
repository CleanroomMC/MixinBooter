package zone.rong.mixinbooter.test;

import net.minecraftforge.fml.common.Mod;

@Mod(modid = TestMod.MOD_ID, name = "MixinBooter Test", version = "1.0", dependencies = "required-after:fermiumbooter@[1.6,);required-after:mixinbooter")
public class TestMod {

    public static final String MOD_ID = "mixinbooter_test";

}
