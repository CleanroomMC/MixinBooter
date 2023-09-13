package zone.rong.mixinbooter;

import com.google.common.eventbus.EventBus;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import zone.rong.mixinbooter.fix.MixinFixer;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name("MixinBooter")
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("MixinBooter");

    public MixinBooterPlugin() {
        addTransformationExclusions();
        initialize();
        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();
        Mixins.addConfiguration("mixin.mixinbooter.init.json");
        LOGGER.info("Initializing MixinExtras...");
        MixinExtrasBootstrap.init();
        MixinFixer.patchAncientModMixinsLoadingMethod();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return "zone.rong.mixinbooter.MixinBooterPlugin$Container";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object coremodList = data.get("coremodList");
        if (coremodList instanceof List) {
            Field fmlPluginWrapper$coreModInstance = null;
            for (Object coremod : (List) coremodList) {
                try {
                    if (fmlPluginWrapper$coreModInstance == null) {
                        fmlPluginWrapper$coreModInstance = coremod.getClass().getField("coreModInstance");
                        fmlPluginWrapper$coreModInstance.setAccessible(true);
                    }
                    Object theMod = fmlPluginWrapper$coreModInstance.get(coremod);
                    if (theMod instanceof IEarlyMixinLoader) {
                        IEarlyMixinLoader loader = (IEarlyMixinLoader) theMod;
                        LOGGER.info("Grabbing {} for its mixins.", loader.getClass());
                        for (String mixinConfig : loader.getMixinConfigs()) {
                            if (loader.shouldMixinConfigQueue(mixinConfig)) {
                                LOGGER.info("Adding {} mixin configuration.", mixinConfig);
                                Mixins.addConfiguration(mixinConfig);
                                loader.onMixinConfigQueued(mixinConfig);
                            }
                        }
                    } else if ("org.spongepowered.mod.SpongeCoremod".equals(theMod.getClass().getName())) {
                        Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.spongeforge.SpongeForgeFixer");
                    }
                } catch (Throwable t) {
                    LOGGER.error("Unexpected error", t);
                }
            }
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    private void addTransformationExclusions() {
        Launch.classLoader.addTransformerExclusion("scala.");
        Launch.classLoader.addTransformerExclusion("com.llamalad7.mixinextras.");
    }

    private void initialize() {
        GlobalProperties.put(GlobalProperties.Keys.CLEANROOM_DISABLE_MIXIN_CONFIGS, new HashSet<>());
    }

    public static class Container extends DummyModContainer {

        public Container() {
            super(new ModMetadata());
            ModMetadata meta = this.getMetadata();
            meta.modId = "mixinbooter";
            meta.name = "MixinBooter";
            meta.description = "A mod that provides the Sponge Mixin library, a standard API for mods to load mixins targeting Minecraft and other mods, and associated useful utilities on 1.8 - 1.12.2";
            meta.credits = "Thanks to LegacyModdingMC + Fabric for providing the initial mixin fork.";
            meta.version = Tags.VERSION;
            meta.logoFile = "/icon.png";
            meta.authorList.add("Rongmario");
        }

        @Override
        public boolean registerBus(EventBus bus, LoadController controller) {
            bus.register(this);
            return true;
        }

    }

}
