package zone.rong.mixinbooter;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.asm.util.asm.ASM;
import zone.rong.mixinbooter.fix.MixinFixer;
import zone.rong.mixinbooter.util.ModDiscoverer;

import java.lang.reflect.Field;
import java.util.*;

@IFMLLoadingPlugin.Name("MixinBooter")
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public static final ILogger LOGGER = MixinService.getService().getLogger("MixinBooter");

    static String getMinecraftVersion() {
        return (String) FMLInjectionData.data()[4];
    }

    public MixinBooterPlugin() {
        this.addTransformationExclusions();
        this.initialize();
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return "zone.rong.mixinbooter.MixinBooterModContainer";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object coremodList = data.get("coremodList");
        if (coremodList instanceof List) {
            Collection<IEarlyMixinLoader> earlyLoaders = this.gatherEarlyLoaders((List) coremodList);
            this.loadEarlyLoaders(earlyLoaders);
        } else {
            throw new RuntimeException("Blackboard property 'coremodList' must be of type List, early loaders were not able to be gathered");
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    private void addTransformationExclusions() {
        Launch.classLoader.addTransformerExclusion("scala.");
        // Launch.classLoader.addTransformerExclusion("com.llamalad7.mixinextras.");
    }

    private void initialize() {
        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();

        Mixins.addConfiguration("mixin.mixinbooter.init.json");

        LOGGER.info("Initializing MixinExtras...");
        this.initMixinExtras();

        MixinFixer.patchAncientModMixinsLoadingMethod();

        LOGGER.info("Gathering present mods...");
        ModDiscoverer.discover(getMinecraftVersion());

        this.afterAll();
    }

    private void initMixinExtras() {
        if (!ASM.isAtLeastVersion(5, 1)) {
            Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.mixinextras.MixinExtrasFixer");
        }
        MixinExtrasBootstrap.init();
    }

    private void afterAll() {
        if (ModDiscoverer.isModPresent("spongeforge")) {
            LOGGER.info("Registering SpongeForgeFixer transformer to solve issues pertaining SpongeForge.");
            Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.spongeforge.SpongeForgeFixer");
            // Eagerly load PrettyPrinter class for transformation
            new PrettyPrinter();
            // Also apply eagerly loading of Event.class in the EventSubscriptionTransformer
            // While technically a Forge bug, it manifests when SpongeForge is installed with Mixin 0.8.5+
            Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.forge.EagerlyLoadEventClassTransformer");
        }
    }

    private Collection<IEarlyMixinLoader> gatherEarlyLoaders(List coremodList) {
        Field fmlPluginWrapper$coreModInstance = null;
        Set<IEarlyMixinLoader> queuedLoaders = new LinkedHashSet<>();
        Context context = new Context(null, ModDiscoverer.getPresentMods()); // For hijackers
        for (Object coremod : coremodList) {
            try {
                if (fmlPluginWrapper$coreModInstance == null) {
                    fmlPluginWrapper$coreModInstance = coremod.getClass().getField("coreModInstance");
                    fmlPluginWrapper$coreModInstance.setAccessible(true);
                }
                Object theMod = fmlPluginWrapper$coreModInstance.get(coremod);
                if (theMod instanceof IMixinConfigHijacker) {
                    IMixinConfigHijacker interceptor = (IMixinConfigHijacker) theMod;
                    LOGGER.info("Loading config hijacker {}.", interceptor.getClass().getName());
                    for (String hijacked : interceptor.getHijackedMixinConfigs(context)) {
                        Config.blacklist(hijacked);
                        LOGGER.info("{} will hijack the mixin config {}", interceptor.getClass().getName(), hijacked);
                    }
                }
                if (theMod instanceof IEarlyMixinLoader) {
                    queuedLoaders.add((IEarlyMixinLoader) theMod);
                }
            } catch (Throwable t) {
                LOGGER.error("Unexpected error", t);
            }
        }
        return queuedLoaders;
    }

    private void loadEarlyLoaders(Collection<IEarlyMixinLoader> queuedLoaders) {
        for (IEarlyMixinLoader queuedLoader : queuedLoaders) {
            LOGGER.info("Loading early loader {} for its mixins.", queuedLoader.getClass().getName());
            try {
                for (String mixinConfig : queuedLoader.getMixinConfigs()) {
                    Context context = new Context(mixinConfig, ModDiscoverer.getPresentMods());
                    if (queuedLoader.shouldMixinConfigQueue(context)) {
                        LOGGER.info("Adding [{}] mixin configuration.", mixinConfig);
                        Mixins.addConfiguration(mixinConfig);
                        queuedLoader.onMixinConfigQueued(context);
                    }
                }
            } catch (Throwable t) {
                LOGGER.error("Failed to execute early loader [{}].", queuedLoader.getClass().getName(), t);
            }
        }
    }

}
