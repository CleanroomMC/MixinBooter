package zone.rong.mixinbooter;

import com.google.common.eventbus.EventBus;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.fml.common.versioning.InvalidVersionSpecificationException;
import net.minecraftforge.fml.common.versioning.VersionRange;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import zone.rong.mixinbooter.fix.MixinFixer;

import java.lang.reflect.Field;
import java.util.*;

@IFMLLoadingPlugin.Name("MixinBooter")
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("MixinBooter");

    static String getMinecraftVersion() {
        return (String) FMLInjectionData.data()[4];
    }

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
            Map<String, IMixinConfigHijacker> hijackers = new HashMap<>();
            Set<IEarlyMixinLoader> queuedLoaders = new LinkedHashSet<>();
            for (Object coremod : (List) coremodList) {
                try {
                    if (fmlPluginWrapper$coreModInstance == null) {
                        fmlPluginWrapper$coreModInstance = coremod.getClass().getField("coreModInstance");
                        fmlPluginWrapper$coreModInstance.setAccessible(true);
                    }
                    Object theMod = fmlPluginWrapper$coreModInstance.get(coremod);
                    if (theMod instanceof IMixinConfigHijacker) {
                        IMixinConfigHijacker interceptor = (IMixinConfigHijacker) theMod;
                        for (String hijacked : interceptor.getHijackedMixinConfigs()) {
                            hijackers.put(hijacked, interceptor);
                        }
                    }
                    if (theMod instanceof IEarlyMixinLoader) {
                        IEarlyMixinLoader loader = (IEarlyMixinLoader) theMod;
                        LOGGER.info("Grabbing {} for its mixins.", loader.getClass());
                        queuedLoaders.add(loader);
                    } else if ("org.spongepowered.mod.SpongeCoremod".equals(theMod.getClass().getName())) {
                        Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.spongeforge.SpongeForgeFixer");
                    }
                } catch (Throwable t) {
                    LOGGER.error("Unexpected error", t);
                }
            }
            for (IEarlyMixinLoader queuedLoader : queuedLoaders) {
                for (String mixinConfig : queuedLoader.getMixinConfigs()) {
                    if (queuedLoader.shouldMixinConfigQueue(mixinConfig)) {
                        IMixinConfigHijacker hijacker = hijackers.get(mixinConfig);
                        if (hijacker != null) {
                            LOGGER.info("Mixin configuration [{}] intercepted by [{}].", mixinConfig, hijacker.getClass().getName());
                        } else {
                            LOGGER.info("Adding {} mixin configuration.", mixinConfig);
                            Mixins.addConfiguration(mixinConfig);
                            queuedLoader.onMixinConfigQueued(mixinConfig);
                        }
                    }
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
            MixinBooterPlugin.LOGGER.info("Initializing MixinBooter's Mod Container.");
            ModMetadata meta = this.getMetadata();
            meta.modId = Tags.MOD_ID;
            meta.name = Tags.MOD_NAME;
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

        @Override
        public Set<ArtifactVersion> getRequirements() {
            try {
                if ("1.12.2".equals(getMinecraftVersion())) {
                    try {
                        return Collections.singleton(new SpongeForgeArtifactVersion());
                    } catch (InvalidVersionSpecificationException e) {
                        throw new RuntimeException(e);
                    }
                }
            } catch (Throwable ignored) { }
            return Collections.emptySet();
        }

        // Thank you SpongeForge ^_^
        private static class SpongeForgeArtifactVersion extends DefaultArtifactVersion {

            public SpongeForgeArtifactVersion() throws InvalidVersionSpecificationException {
                super("spongeforge", VersionRange.createFromVersionSpec("[7.4.8,)"));
            }

            @Override
            public boolean containsVersion(ArtifactVersion source) {
                if (source == this) {
                    return true;
                }
                String version = source.getVersionString();
                String[] hyphenSplits = version.split("-");
                if (hyphenSplits.length > 1) {
                    if (hyphenSplits[hyphenSplits.length - 1].startsWith("RC")) {
                        version = hyphenSplits[hyphenSplits.length - 2];
                    } else {
                        version = hyphenSplits[hyphenSplits.length - 1];
                    }
                }
                source = new DefaultArtifactVersion(source.getLabel(), version);
                return super.containsVersion(source);
            }
        }

    }

}
