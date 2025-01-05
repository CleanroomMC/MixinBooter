package zone.rong.mixinbooter;

import com.google.common.eventbus.EventBus;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
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
import org.spongepowered.asm.mixin.ModUtil;
import org.spongepowered.asm.mixin.transformer.Config;
import zone.rong.mixinbooter.fix.MixinFixer;
import zone.rong.mixinbooter.util.MockedArtifactVersionAdapter;
import zone.rong.mixinbooter.util.MockedMetadataCollection;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

@IFMLLoadingPlugin.Name("MixinBooter")
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("MixinBooter");

    private static final Map<String, String> presentMods = new HashMap<>();
    private static final Map<String, IMixinConfigHijacker> configHijackers = new HashMap<>();

    private static Field modApiManager$dataTable;

    public static IMixinConfigHijacker getHijacker(String configName) {
        return configHijackers.get(configName);
    }

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
        Launch.classLoader.addTransformerExclusion("com.llamalad7.mixinextras.");
    }

    private void initialize() {
        GlobalProperties.put(GlobalProperties.Keys.CLEANROOM_DISABLE_MIXIN_CONFIGS, new HashSet<>());

        LOGGER.info("Initializing Mixins...");
        MixinBootstrap.init();

        Mixins.addConfiguration("mixin.mixinbooter.init.json");

        LOGGER.info("Initializing MixinExtras...");
        MixinExtrasBootstrap.init();

        MixinFixer.patchAncientModMixinsLoadingMethod();

        LOGGER.info("Gathering present mods...");
        this.gatherPresentMods();
    }

    private void gatherPresentMods() {
        Gson gson = new GsonBuilder().registerTypeAdapter(ArtifactVersion.class, new MockedArtifactVersionAdapter())
                .create();
        try {
            Enumeration<URL> resources = Launch.classLoader.getResources("mcmod.info");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String fileName = getJarNameFromResource(url);
                if (fileName != null) {
                    String modId = parseMcmodInfo(gson, url);
                    if (modId != null) {
                        presentMods.put(fileName, modId);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to gather present mods", e);
        }
        logInfo("Finished gathering %d mods...", presentMods.size());
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

    private String parseMcmodInfo(Gson gson, URL url) {
        try {
            JsonReader reader = new JsonReader(new InputStreamReader(url.openStream()));
            reader.setLenient(true);
            JsonElement root = gson.fromJson(reader, JsonElement.class);
            if (root.isJsonArray()) {
                return gson.fromJson(new InputStreamReader(url.openStream()), ModMetadata[].class)[0].modId;
            } else {
                return gson.fromJson(new InputStreamReader(url.openStream()), MockedMetadataCollection.class).modList[0].modId;
            }
        } catch (Throwable t) {
            logError("Failed to parse mcmod.info for %s", t, url);
            return null;
        }
    }

    private Collection<IEarlyMixinLoader> gatherEarlyLoaders(List coremodList) {
        Field fmlPluginWrapper$coreModInstance = null;
        Set<IEarlyMixinLoader> queuedLoaders = new LinkedHashSet<>();
        for (Object coremod : coremodList) {
            try {
                if (fmlPluginWrapper$coreModInstance == null) {
                    fmlPluginWrapper$coreModInstance = coremod.getClass().getField("coreModInstance");
                    fmlPluginWrapper$coreModInstance.setAccessible(true);
                }
                Object theMod = fmlPluginWrapper$coreModInstance.get(coremod);
                if (theMod instanceof IMixinConfigHijacker) {
                    IMixinConfigHijacker interceptor = (IMixinConfigHijacker) theMod;
                    for (String hijacked : interceptor.getHijackedMixinConfigs()) {
                        configHijackers.put(hijacked, interceptor);
                    }
                }
                if (theMod instanceof IEarlyMixinLoader) {
                    queuedLoaders.add((IEarlyMixinLoader) theMod);
                } else if ("org.spongepowered.mod.SpongeCoremod".equals(theMod.getClass().getName())) {
                    LOGGER.info("Registering SpongeForgeFixer transformer to solve issues pertaining SpongeForge.");
                    Launch.classLoader.registerTransformer("zone.rong.mixinbooter.fix.spongeforge.SpongeForgeFixer");
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
                    Context context = new Context(mixinConfig, presentMods.values());
                    if (queuedLoader.shouldMixinConfigQueue(context)) {
                        IMixinConfigHijacker hijacker = getHijacker(mixinConfig);
                        if (hijacker != null) {
                            logInfo("Mixin configuration [%s] intercepted by [%s].", mixinConfig, hijacker.getClass().getName());
                        } else {
                            logInfo("Adding [%s] mixin configuration.", mixinConfig);
                            Mixins.addConfiguration(mixinConfig);
                            queuedLoader.onMixinConfigQueued(context);
                        }
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
                String modId = presentMods.get(jar);
                if (modId != null) {
                    return modId;
                }
            }
        }
        return ModUtil.UNKNOWN_OWNER;
    }

    public static class Container extends DummyModContainer {

        public Container() {
            super(new ModMetadata());
            MixinBooterPlugin.LOGGER.info("Initializing MixinBooter's Mod Container.");
            ModMetadata meta = this.getMetadata();
            meta.modId = Tags.MOD_ID;
            meta.name = Tags.MOD_NAME;
            meta.description = "A mod that provides the Sponge Mixin library, a standard API for mods to load mixins targeting Minecraft and other mods, and associated useful utilities on 1.8 - 1.12.2.";
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
