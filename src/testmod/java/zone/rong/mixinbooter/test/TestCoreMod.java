package zone.rong.mixinbooter.test;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixins;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test coremod exercising registration pipeline:
 * <b>IEarlyMixinLoader</b> ({@link #getMixinConfigs()}) -- MixinBooter gathers this from the
 *       coremod list in its {@code injectData} and queues {@code mixin.early_test.json}.
 * Registered for dev via {@code -Dfml.coreMods.load=...,zone.rong.mixinbooter.test.TestCoreMod} in build.gradle.
 */
@IFMLLoadingPlugin.Name("MixinBooter Test CoreMod")
public class TestCoreMod implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixin.early_test.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

}
