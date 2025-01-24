package zone.rong.mixinbooter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.ModUtil;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.asm.util.asm.ASM;
import zone.rong.mixinbooter.fix.MixinFixer;
import zone.rong.mixinbooter.util.MockedModMetadata;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

@IFMLLoadingPlugin.Name("MixinBooter")
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("MixinBooter");

    private static final Map<String, String> presentJarsToMods = new HashMap<>();
    private static final Set<String> presentMods = new HashSet<>();
    private static final Set<String> unmodifiablePresentMods = Collections.unmodifiableSet(presentMods);

    private static Field modApiManager$dataTable;

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
            this.recordConfigOwners();
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
        GlobalProperties.put(GlobalProperties.Keys.CLEANROOM_DISABLE_MIXIN_CONFIGS, new HashSet<>());

        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();

        Mixins.addConfiguration("mixin.mixinbooter.init.json");

        LOGGER.info("Initializing MixinExtras...");
        this.initMixinExtras();

        MixinFixer.patchAncientModMixinsLoadingMethod();

        LOGGER.info("Gathering present mods...");
        this.gatherPresentMods();

        this.afterAll();
    }

    private void initMixinExtras() {
        if (!ASM.isAtLeastVersion(5, 1)) {
            Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.mixinextras.MixinExtrasFixer");
        }
        MixinExtrasBootstrap.init();
    }

    private void afterAll() {
        if (unmodifiablePresentMods.contains("spongeforge")) {
            LOGGER.info("Registering SpongeForgeFixer transformer to solve issues pertaining SpongeForge.");
            Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.spongeforge.SpongeForgeFixer");
            // Eagerly load PrettyPrinter class for transformation
            new PrettyPrinter();
            // Also apply eagerly loading of Event.class in the EventSubscriptionTransformer
            // While technically a Forge bug, it manifests when SpongeForge is installed with Mixin 0.8.5+
            Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.forge.EagerlyLoadEventClassTransformer");
        }
    }

    private void gatherPresentMods() {
        Gson gson = new GsonBuilder().create(); // TODO: Provide versioning for mods?
        try {
            Enumeration<URL> resources = Launch.classLoader.getResources("mcmod.info");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String fileName = getJarNameFromResource(url);
                if (fileName != null) {
                    List<String> modIds = parseMcmodInfo(gson, url);
                    if (!modIds.isEmpty()) {
                        presentJarsToMods.put(fileName, modIds.get(0));
                    }
                    presentMods.addAll(modIds);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to gather present mods", e);
        }
        logInfo("Finished gathering %d coremods...", unmodifiablePresentMods.size());
    }

    private String getJarNameFromResource(URL url) {
        if (url.getPath().contains("!/")) {
            String filePath = url.getPath().split("!/")[0];
            String[] parts = filePath.split("/");
            if (parts.length != 0) {
                return parts[parts.length - 1];
            }
        }
        return null;
    }

    private List<String> parseMcmodInfo(Gson gson, URL url) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(url.openStream()));
            reader.setLenient(true);
            List<String> ids = new ArrayList<>();
            for (MockedModMetadata meta : gson.fromJson(new InputStreamReader(url.openStream()), MockedModMetadata[].class)) {
                if (meta.modid != null) {
                    ids.add(meta.modid);
                }
            }
            return ids;
        } catch (Throwable t) {
            logError("Failed to parse mcmod.info for %s", t, url);
        }
        return Collections.emptyList();
    }

    private Collection<IEarlyMixinLoader> gatherEarlyLoaders(List coremodList) {
        Field fmlPluginWrapper$coreModInstance = null;
        Set<IEarlyMixinLoader> queuedLoaders = new LinkedHashSet<>();
        Collection<String> disabledConfigs = GlobalProperties.get(GlobalProperties.Keys.CLEANROOM_DISABLE_MIXIN_CONFIGS);
        Context context = new Context(null, unmodifiablePresentMods); // For hijackers
        for (Object coremod : coremodList) {
            try {
                if (fmlPluginWrapper$coreModInstance == null) {
                    fmlPluginWrapper$coreModInstance = coremod.getClass().getField("coreModInstance");
                    fmlPluginWrapper$coreModInstance.setAccessible(true);
                }
                Object theMod = fmlPluginWrapper$coreModInstance.get(coremod);
                if (theMod instanceof IMixinConfigHijacker) {
                    IMixinConfigHijacker interceptor = (IMixinConfigHijacker) theMod;
                    logInfo("Loading config hijacker %s.", interceptor.getClass().getName());
                    for (String hijacked : interceptor.getHijackedMixinConfigs(context)) {
                        disabledConfigs.add(hijacked);
                        logInfo("%s will hijack the mixin config %s", interceptor.getClass().getName(), hijacked);
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
            logInfo("Loading early loader %s for its mixins.", queuedLoader.getClass().getName());
            try {
                for (String mixinConfig : queuedLoader.getMixinConfigs()) {
                    Context context = new Context(mixinConfig, unmodifiablePresentMods);
                    if (queuedLoader.shouldMixinConfigQueue(context)) {
                        logInfo("Adding [%s] mixin configuration.", mixinConfig);
                        Mixins.addConfiguration(mixinConfig);
                        queuedLoader.onMixinConfigQueued(context);
                    }
                }
            } catch (Throwable t) {
                logError("Failed to execute early loader [%s].", t, queuedLoader.getClass().getName());
            }
        }
    }

    private void recordConfigOwners() {
        for (Config config : Mixins.getConfigs()) {
            if (!config.getConfig().hasDecoration(ModUtil.OWNER_DECORATOR)) {
                config.getConfig().decorate(ModUtil.OWNER_DECORATOR, (Supplier) () -> this.retrieveConfigOwner(config));
            }
        }
    }

    private String retrieveConfigOwner(Config config) {
        if (modApiManager$dataTable == null) {
            try {
                modApiManager$dataTable = ModAPIManager.class.getDeclaredField("dataTable");
                modApiManager$dataTable.setAccessible(true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Unable to reflectively retrieve ModAPIManager#dataTable", e);
            }
        }
        try {
            ASMDataTable table = (ASMDataTable) modApiManager$dataTable.get(ModAPIManager.INSTANCE);
            if (table != null) {
                String pkg = config.getConfig().getMixinPackage();
                pkg = pkg.charAt(pkg.length() - 1) == '.' ? pkg.substring(0, pkg.length() - 1) : pkg;
                ModCandidate candidate = table.getCandidatesFor(pkg).stream().findFirst().orElse(null);
                if (candidate != null) {
                    ModContainer container = candidate.getContainedMods().get(0);
                    if (container != null) {
                        return container.getModId();
                    }
                }
            }
        } catch (IllegalAccessException ignore) { }
        URL url = Launch.classLoader.getResource(config.getName());
        if (url != null) {
            String jar = this.getJarNameFromResource(url);
            if (jar != null) {
                String modId = presentJarsToMods.get(jar);
                if (modId != null) {
                    return modId;
                }
            }
        }
        return ModUtil.UNKNOWN_OWNER;
    }

    /*
     * Minecraft 1.8.x uses a beta version of Log4j2 with a slightly different
     * API for parameterized logging than ended up in the releases used by 1.12+.
     *
     * The following methods act as a workaround for that issue while keeping the
     * performance conscious "log only if enabled" approach employed by Log4j2 internally.
     */

    @SuppressWarnings("StringConcatenationArgumentToLogCall")
    public static void logInfo(String message, Object... params) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format(message, params));
        }
    }

    @SuppressWarnings("StringConcatenationArgumentToLogCall")
    public static void logError(String message, Throwable t, Object... params) {
        if (LOGGER.isErrorEnabled()) {
            LOGGER.error(String.format(message, params), t);
        }
    }
}
