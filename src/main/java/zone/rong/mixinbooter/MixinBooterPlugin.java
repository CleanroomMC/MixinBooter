package zone.rong.mixinbooter;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.asm.ASM;
import zone.rong.mixinbooter.util.Environment;
import zone.rong.mixinbooter.service.ModDiscoverer;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

@IFMLLoadingPlugin.Name(Tags.MOD_NAME)
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public MixinBooterPlugin() {
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
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        ModDiscoverer.applyForceLoadAsMod();
        Object coremodList = data.get("coremodList");
        if (coremodList instanceof List) {
            Collection<IEarlyMixinLoader> earlyLoaders = this.gatherEarlyLoaders((List) coremodList);
            this.loadEarlyLoaders(earlyLoaders);
        } else {
            throw new RuntimeException("Blackboard property 'coremodList' must be of type List, early loaders were not able to be gathered");
        }
        MixinBootstrap.getPlatform().inject();
    }

    /**
     * An order of calls are made before we initialize later plugins and mixin (subsystem & mods):
     * {@link #injectSelfIntoAppClassLoader()} runs first to ensure behaviour is replicated
     * from when MixinBooter used to use MixinTweaker as a starting point, now we have this plugin class
     * run first.
     * {@link #installClassLoaderExclusions()} is for making sure classloading is propagated right.
     * {@code mixin.bootstrapService} & {@code mixin.service} is set to
     * {@link zone.rong.mixinbooter.service.MixinServiceBootstrap}
     * and {@link zone.rong.mixinbooter.service.MixinBooterService} respectively.
     * Then the mixin subsystem is initialized - {@link MixinBootstrap#init()}
     * Config is read straight afterwards.
     */
    private void initialize() {
        this.injectSelfIntoAppClassLoader();
        this.installClassLoaderExclusionsAndTransformers();

        MixinBooterConfig.load();

        System.setProperty("mixin.bootstrapService", "zone.rong.mixinbooter.service.MixinServiceBootstrap");
        System.setProperty("mixin.service", "zone.rong.mixinbooter.service.MixinBooterService");

        MixinBootstrap.init();
        ModDiscoverer.discover();
        this.registerCoremodsRescuer();
    }

    /**
     * Force-adds MixinBooter into the parent AppClassLoader, mirroring what Forge does
     * in {@link net.minecraftforge.fml.relauncher.CoreModManager#discoverCoreMods(File, LaunchClassLoader)}
     * when dealing with cascading tweak classes (which we used to utilize when adding {@code MixinTweaker}.
     * No-op (and harmless) in dev, where the jar is already on the AppClassLoader.
     */
    private void injectSelfIntoAppClassLoader() {
        if (Environment.inDev()) {
            return;
        }
        ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();
        if (!(appClassLoader instanceof URLClassLoader)) {
            return;
        }
        try {
            URL self = MixinBooterPlugin.class.getProtectionDomain().getCodeSource().getLocation();
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(appClassLoader, self);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to add MixinBooter into the parent ClassLoader", t);
        }
    }

    private void installClassLoaderExclusionsAndTransformers() {
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.launch.");
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.service.");
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.mixin.");
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.logging.");
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.util.");
        Launch.classLoader.addClassLoaderExclusion("org.spongepowered.asm.lib.");
        Launch.classLoader.addClassLoaderExclusion("org.objectweb.asm.");
        Launch.classLoader.addClassLoaderExclusion("zone.rong.mixinbooter.service.");
        Launch.classLoader.registerTransformer("zone.rong.mixinbooter.service.ClassLoadTracer");
        if (!ASM.isAtLeastVersion(5, 1)) {
            Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.mixinextras.MixinExtrasFixer");
        }
    }

    /**
     * Registers {@link zone.rong.mixinbooter.service.CoremodsRescuer} at the head of
     * LaunchWrapper's tweak class list so it is constructed before {@code FMLInjectionAndSortingTweaker}.
     * Coremods Forge dropped (those that declare a TweakClass alongside their FMLCorePlugin)
     * are then loaded from a tweaker constructor, the only point at which the {@code Tweaks} list is safely writeable.
     */
    @SuppressWarnings("unchecked")
    private void registerCoremodsRescuer() {
        List<String> tweakClasses = (List<String>) Launch.blackboard.get("TweakClasses");
        if (tweakClasses != null) {
            tweakClasses.add(0, "zone.rong.mixinbooter.service.CoremodsRescuer");
        }
    }

    private Collection<IEarlyMixinLoader> gatherEarlyLoaders(List coremodList) {
        ILogger logger = MixinService.getService().getLogger(Tags.MOD_NAME);
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
                    logger.info("Loading config hijacker {}.", interceptor.getClass().getName());
                    for (String hijacked : interceptor.getHijackedMixinConfigs(context)) {
                        Config.blacklist(hijacked);
                        logger.info("{} will hijack the mixin config {}", interceptor.getClass().getName(), hijacked);
                    }
                }
                if (theMod instanceof IEarlyMixinLoader) {
                    queuedLoaders.add((IEarlyMixinLoader) theMod);
                }
            } catch (Throwable t) {
                logger.error("Unexpected error", t);
            }
        }
        return queuedLoaders;
    }

    private void loadEarlyLoaders(Collection<IEarlyMixinLoader> queuedLoaders) {
        ILogger logger = MixinService.getService().getLogger(Tags.MOD_NAME);
        for (IEarlyMixinLoader queuedLoader : queuedLoaders) {
            logger.info("Loading early loader {} for its mixins.", queuedLoader.getClass().getName());
            try {
                for (String mixinConfig : queuedLoader.getMixinConfigs()) {
                    Context context = new Context(mixinConfig, ModDiscoverer.getPresentMods());
                    if (queuedLoader.shouldMixinConfigQueue(context)) {
                        logger.info("Adding [{}] mixin configuration.", mixinConfig);
                        Mixins.addConfiguration(mixinConfig);
                        queuedLoader.onMixinConfigQueued(context);
                    }
                }
            } catch (Throwable t) {
                logger.error("Failed to execute early loader [{}].", queuedLoader.getClass().getName(), t);
            }
        }
    }

}
