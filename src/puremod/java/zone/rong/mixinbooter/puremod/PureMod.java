package zone.rong.mixinbooter.puremod;

import net.minecraftforge.fml.common.Mod;
import zone.rong.mixinbooter.service.ModDiscoverer;

/**
 * A plain {@code @Mod} jar with NO {@code FMLCorePlugin}. Only a {@code MixinConfigs} manifest entry.
 * Forge would only add such a jar to the classloader before {@link net.minecraftforge.fml.common.LoaderState.ModState#CONSTRUCTING}
 * which is too late for the PREINIT manifest scan. {@link ModDiscoverer}
 * pulls it into the LaunchClassLoader during discovery so its able to load its mixins early.
 */
@Mod(modid = PureMod.MOD_ID, name = "MixinBooter Pure Mod Test", version = "1.0")
public class PureMod {

    public static final String MOD_ID = "puremod_test";

}
