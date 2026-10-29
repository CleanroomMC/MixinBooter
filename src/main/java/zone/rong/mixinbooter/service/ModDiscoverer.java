package zone.rong.mixinbooter.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.Repository;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Constants.ManifestAttributes;
import zone.rong.mixinbooter.Tags;
import zone.rong.mixinbooter.util.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Discovers all mods present in the game directory at coremod init time
 * independently of {@link net.minecraftforge.fml.common.Loader} which is not available early.
 * Builds a bidirectional mod-id and file mapping used for dependency checking.
 */
public final class ModDiscoverer {

    private static final ILogger LOGGER = MixinService.getService().getLogger(Tags.MOD_NAME);
    private static final String FORCE_LOAD_AS_MOD = "ForceLoadAsMod";
    private static final String COREMOD_CONTAINS_FML_MOD = "FMLCorePluginContainsFMLMod";
    private static final String FML_CORE_PLUGIN = "FMLCorePlugin";
    private static final String TWEAK_CLASSES = "TweakClasses";
    private static final Map<String, Mod> mods = new HashMap<>();
    // Every scanned jar by absolute file, in scan order so dropped coremods are rescued in the order Forge found them
    private static final Map<File, Jar> jars = new LinkedHashMap<>();
    // Jar names to un-ignore, mapped to whether they also have to be reparsed
    private static final Map<String, Boolean> forceLoadAsModFiles = new HashMap<>();
    private static final List<String> rescuedTweakClasses = new ArrayList<>();
    private static final Collection<String> ALLOWED_TWEAKERS = Arrays.asList(
            "org.spongepowered.asm.launch.MixinTweaker",
            "com.replaymod.core.tweaker.ReplayModTweaker"
    );
    // Very naive list, you should use Cleanroom instead of Forge + MixinBooter!
    private static final String[] SHADED_LIBRARIES = {
            "org/spongepowered/", "com/llamalad7/", "org/objectweb/", "com/google/", "it/unimi/", "kotlin/", "scala/",
            "net/minecraft/", "net/minecraftforge/", "com/mojang/", "org/apache/", "io/netty/", "gnu/trove/", "akka/",
            "com/ibm/", "org/lwjgl/", "org/jline/", "com/sun/", "javax/", "org/jetbrains/", "org/intellij/",
            "org/checkerframework/", "org/slf4j/", "com/fasterxml/", "org/yaml/"
    };
    private static final byte[][] ANNOTATIONS = {
            ascii(ClassScanner.MOD_ANNOTATION), ascii(ClassScanner.MIXIN_CONFIGURATION), ascii(ClassScanner.FERMIUM_CONFIG)
    };
    private static final String FERMIUMBOOTER = "fermiumbooter";
    // Only the mod jar ships FB support, which supersedes the standalone mod on 1.12.2
    private static final boolean FB_SUPPORT = "1.12.2".equals(Environment.minecraftVersion()) &&
            ModDiscoverer.class.getClassLoader().getResource("zone/rong/mixinbooter/fb/FBMod.class") != null;
    // Measured on 20 to 90 jar packs with 4 and 8 cores, a second thread cuts discovery by a third or more past 32 jars.
    // A third helps big packs a little and more threads do nothing, as the largest jar bounds the walk.
    private static final int JARS_PER_THREAD = 32;
    private static final int MAX_THREADS = 3;
    // Reused for every class read on the same discovery thread
    private static final ThreadLocal<byte[]> CLASS_BUFFER = ThreadLocal.withInitial(() -> new byte[1 << 16]);

    private static boolean discovered = false;
    // Mixin connectors have their exceptions swallowed, so crashes wait until every connector has run
    private static List<String> crashes = new ArrayList<>();

    private ModDiscoverer() { }

    /**
     * Returns whether any discovered jar declares the given mod id.
     *
     * @param modId the mod id to test
     * @return {@code true} if at least one scanned jar declares the id
     */
    public static boolean isModPresent(String modId) {
        return mods.containsKey(modId);
    }

    /**
     * @return the mod's name, from {@code @Mod} or else {@code mcmod.info}, or {@code null} if unknown
     */
    public static String getModName(String modId) {
        Mod mod = mods.get(modId);
        return mod == null ? null : mod.name;
    }

    /**
     * @return the mod's version, from {@code @Mod} or else {@code mcmod.info}, or {@code null} if unknown
     */
    public static String getModVersion(String modId) {
        Mod mod = mods.get(modId);
        return mod == null ? null : mod.version;
    }

    /**
     * Returns whether the given mod is present under exactly the given name, to tell apart forks sharing a mod id.
     * The {@code @Mod} name is preferred over the {@code mcmod.info} name.
     *
     * @param modId the mod id to test
     * @param name  the expected name
     * @return {@code true} if the mod is present and named exactly so
     */
    public static boolean isModNamed(String modId, String name) {
        return name != null && name.equals(getModName(modId));
    }

    /**
     * Returns whether the given mod is present with a version in the given range.
     * The {@code @Mod} version is preferred over the {@code mcmod.info} version.
     *
     * @param modId the mod id to test
     * @param range a range in the same format as {@code @Mod(dependencies = ...)} such as {@code [1.2,2.0)}, or an exact version
     * @return {@code true} if the mod is present and its version is in range, {@code false} if the range is invalid
     */
    public static boolean isModVersionInRange(String modId, String range) {
        String version = getModVersion(modId);
        if (version == null || range == null) {
            return false;
        }
        if (range.equals(version)) {
            return true;
        }
        try {
            return VersionRange.createFromVersionSpec(range).containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }

    /**
     * Returns the ids of every mod discovered across all scanned jars.
     *
     * @return an unmodifiable view of all present mod ids
     */
    public static Set<String> getPresentMods() {
        return Collections.unmodifiableSet(mods.keySet());
    }

    /**
     * Returns the jars that declare the given mod id.
     * A single mod id may be shipped by more than one jar (e.g. duplicate installs), hence a set.
     *
     * @param modId the mod id to resolve
     * @return the jars declaring that mod id (empty if none)
     */
    public static Set<File> getModSources(String modId) {
        Mod mod = mods.get(modId);
        return mod == null ? Collections.emptySet() : Collections.unmodifiableSet(mod.sources);
    }

    /**
     * Returns the id of the mod owning the given jar.
     * Use {@link #getModsFromSource(File)} instead if you need every declared id.
     *
     * @param source the jar to resolve
     * @return the owning mod's id (first declared via {@code mcmod.info}, else its {@code @Mod} annotation),
     *         or {@code null} if the jar declares no mod
     */
    public static String getModFromSource(File source) {
        Set<String> ids = getModsFromSource(source);
        return ids.isEmpty() ? null : ids.iterator().next();
    }

    /**
     * Returns the ids of the mod(s) owning the given jar, resolved during {@link #discover()} from its
     * {@code mcmod.info} or, when that declares none, its {@code @Mod} annotation. A jar may declare more
     * than one mod (via {@code mcmod.info}'s modList), hence the set.
     *
     * @param source the jar to inspect
     * @return the mod ids the jar declares (empty if none)
     */
    public static Set<String> getModsFromSource(File source) {
        Jar jar = jars.get(source.getAbsoluteFile());
        return jar == null ? Collections.emptySet() : Collections.unmodifiableSet(jar.modIds);
    }

    /**
     * Honors the {@code ForceLoadAsMod} manifest key for every jar collected during {@link #discover()}.
     * Replicating the only behavior the removed {@code MixinPlatformAgentFMLLegacy} still provided which was
     * un-ignoring jars so Forge would load it as mods. Forge unconditionally adds cascading-tweaker jars
     * to its ignored list and never reaches its own {@code FMLCorePluginContainsFMLMod} handling for them.
     * Without this functionality those jars would silently fail to load as mods.
     * Must run after {@link CoreModManager#discoverCoreMods} has fully populated the ignored list, i.e. from
     * {@code injectData} and not from the plugin constructor.
     */
    public static void applyForceLoadAsMod() {
        if (forceLoadAsModFiles.isEmpty()) {
            return;
        }
        List<String> ignored = getIgnoredMods();
        List<String> reparseable = CoreModManager.getReparseableCoremods();
        for (Map.Entry<String, Boolean> entry : forceLoadAsModFiles.entrySet()) {
            String name = entry.getKey();
            ignored.remove(name);
            if (entry.getValue() && !reparseable.contains(name)) {
                reparseable.add(name);
            }
        }
    }

    /**
     * Loads the {@code FMLCorePlugin} of every jar that also declares a {@code TweakClass},
     * which Forge's {@link CoreModManager#discoverCoreMods} skips.
     * The removed {@code MixinPlatformAgentFMLLegacy} re-injected these via {@code CoreModManager.loadCoreMod}
     * and this replicates that. This must run from {@link CoremodsRescuer}'s constructor while LaunchWrapper is
     * iterating {@code TweakClasses}, because rescued coremods such as Sledgehammer may add directly to
     * {@code Tweaks}. Any {@code TweakClasses} added during rescue are captured and replayed later to avoid
     * corrupting LaunchWrapper's active {@code TweakClasses} iterator.
     */
    public static void rescueDroppedCoremods() {
        Map<String, File> coremods = new LinkedHashMap<>();
        for (Map.Entry<File, Jar> entry : jars.entrySet()) {
            String coremod = entry.getValue().droppedCoremod;
            if (coremod != null) {
                coremods.putIfAbsent(coremod, entry.getKey());
            }
        }
        if (coremods.isEmpty()) {
            return;
        }
        Method loadCoreMod;
//        Field loadPlugins, location;
        try {
            loadCoreMod = CoreModManager.class.getDeclaredMethod("loadCoreMod", LaunchClassLoader.class, String.class, File.class);
            loadCoreMod.setAccessible(true);
//            loadPlugins = CoreModManager.class.getDeclaredField("loadPlugins");
//            loadPlugins.setAccessible(true);
//            location = Class.forName("net.minecraftforge.fml.relauncher.CoreModManager$FMLPluginWrapper", true, Launch.classLoader).getDeclaredField("location")
//            location.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.error("Unable to access crucial internals. Coremods declared alongside a TweakClass will not be loaded.", t);
            return;
        }
        Iterator<Map.Entry<String, File>> iterator = coremods.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, File> entry = iterator.next();
            try {
                Launch.classLoader.addURL(entry.getValue().toURI().toURL());
            } catch (MalformedURLException e) {
                LOGGER.error("Failed to manually load coremod {} from {}.", entry.getKey(), entry.getValue().getName(), e);
                iterator.remove();
            }
        }

        List<String> launchTweakClasses = getLaunchTweakClasses();
        CapturedTweakClasses shadowTweakClasses = new CapturedTweakClasses(launchTweakClasses);
        setLaunchTweakClasses(shadowTweakClasses);
        try {
            for (Map.Entry<String, File> entry : coremods.entrySet()) {
                String coremod = entry.getKey();
                File jar = entry.getValue();
                try {
                    Object wrapper = loadCoreMod.invoke(null, Launch.classLoader, coremod, jar);
                    if (wrapper != null) {
                        LOGGER.warn("{} declares both a TweakClass and FMLCorePlugin. Forge skips the coremod in this case and {} was loaded manually. Ship it as a normal coremod without a TweakClass.", jar.getName(), coremod);
                    } else {
                        LOGGER.error("Failed to manually load coremod {} from {}.", coremod, jar.getName());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to manually load coremod {} from {}.", coremod, jar.getName(), e);
                }
            }
        } finally {
            setLaunchTweakClasses(launchTweakClasses);
        }
        rescuedTweakClasses.addAll(shadowTweakClasses.getCapturedTweakClasses());
    }

    /**
     * Internal usage, adds a mods folder jar to the classloader before Forge does.
     * The reparseable list keeps Forge from treating the jar as a classpath mod while still discovering it
     * from the mods folder. Must run after CoreModManager#discoverCoreMods has fully populated the ignored list.
     */
    public static void addToClassLoader(File jar) {
        String name = jar.getName();
        // Ignored jars are either on the classloader already (coremods, tweakers) or deliberately left off it by Forge
        if (getIgnoredMods().contains(name) || CoreModManager.getReparseableCoremods().contains(name)) {
            return;
        }
        try {
            URL url = jar.toURI().toURL();
            for (URL present : Launch.classLoader.getURLs()) {
                if (url.toString().equals(present.toString())) {
                    return;
                }
            }
            Launch.classLoader.addURL(url);
            CoreModManager.getReparseableCoremods().add(name);
            LOGGER.info("Added {} to the classloader early to reach its mixin configurations.", name);
        } catch (MalformedURLException e) {
            LOGGER.error("Failed to add {} to the classloader early.", name, e);
        }
    }

    // Forge for 1.8 names it getLoadedCoremods
    @SuppressWarnings("unchecked")
    static List<String> getIgnoredMods() {
        if (!"1.8".equals(Environment.minecraftVersion())) {
            return CoreModManager.getIgnoredMods();
        }
        try {
            return (List<String>) CoreModManager.class.getMethod("getLoadedCoremods").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to read Forge's ignored mods", e);
        }
    }

    /**
     * Internal usage, adds the jars with {@code @MixinConfiguration} fields to the classloader and returns their classes.
     */
    public static List<String> mixinConfigurationClasses() {
        List<String> classes = new ArrayList<>();
        for (Map.Entry<File, Jar> entry : jars.entrySet()) {
            if (entry.getValue().mixinConfigurations != null) {
                addToClassLoader(entry.getKey());
                classes.addAll(entry.getValue().mixinConfigurations);
            }
        }
        return classes;
    }

    /**
     * Internal usage, the coremod jars and the jars declaring a dependency on the given mod,
     * mapped to whether they declare it.
     */
    public static Map<File, Boolean> coremodsAndDependents(String modId) {
        Map<File, Boolean> found = new LinkedHashMap<>();
        for (Map.Entry<File, Jar> entry : jars.entrySet()) {
            Jar jar = entry.getValue();
            if (jar.modIds.contains(Tags.MOD_ID)) {
                continue;
            }
            boolean dependent = jar.dependencies.contains(modId);
            if (jar.coremod || dependent) {
                found.put(entry.getKey(), dependent);
            }
        }
        return found;
    }

    /**
     * Internal usage, the class entries of a coremod or a jar depending on fermiumbooter that are annotated with its
     * {@code @MixinConfig}.
     */
    public static List<String> fermiumConfigClasses(File jar) {
        Jar scanned = jars.get(jar.getAbsoluteFile());
        return scanned == null ? Collections.emptyList() : scanned.fermiumConfigs;
    }

    /**
     * Internal usage, crashes the game once every mixin connector has run.
     */
    public static void crash(String message) {
        LOGGER.fatal(message);
        if (crashes == null) {
            throw new IllegalStateException(message);
        }
        crashes.add(message);
    }

    /**
     * Internal usage
     */
    public static void flushCrashes() {
        List<String> pending = crashes;
        crashes = null;
        if (!pending.isEmpty()) {
            throw new IllegalStateException(String.join(" ", pending));
        }
    }

    public static void flushRescuedTweakClasses() {
        if (rescuedTweakClasses.isEmpty()) {
            return;
        }
        List<String> tweakClasses = getLaunchTweakClasses();
        if (tweakClasses == null) {
            LOGGER.error("Unable to retrieve rescued tweak classes because LaunchWrapper's TweakClasses list is unavailable.");
            rescuedTweakClasses.clear();
            return;
        }
        for (String tweakClass : rescuedTweakClasses) {
            if (!tweakClasses.contains(tweakClass)) {
                LOGGER.warn("Retrieving tweak class {} added by a rescued coremod.", tweakClass);
                tweakClasses.add(tweakClass);
            }
        }
        rescuedTweakClasses.clear();
    }

    /**
     * Gathers the same candidate set FML resolves in {@link CoreModManager#discoverCoreMods} (the flat
     * {@code mods/} and {@code mods/<version>} directories, command-line {@code --mods}, and contained
     * dependencies extracted into {@code memory_repo} or the libraries directory) and supplements it with
     * classpath entries already on the LaunchClassLoader for the dev environment.
     * Must be called once before FML's own mod discovery runs.
     */
    public static void discover() {
        if (discovered) {
            return;
        }
        discovered = true;

        long startTime = System.currentTimeMillis();
        Gson gson;
        try {
            gson = new GsonBuilder().setLenient().create();
        } catch (NoSuchMethodError e) {
            // Older Gson bundled on 1.8.x lacks setLenient()
            gson = new GsonBuilder().create();
        }

        List<Runnable> scans = new ArrayList<>();
        for (File candidate : gatherCandidates()) {
            queueScan(scans, gson, candidate, true);
        }

        // Only the dev environment loads mods from the classpath
        if (Environment.inDev()) {
            for (URL url : Launch.classLoader.getURLs()) {
                try {
                    queueScan(scans, gson, new File(url.toURI()), false);
                } catch (URISyntaxException ignored) { }
            }
        }

        runScans(scans);
        // Pool threads end with the pool, the main thread keeps its buffer unless removed
        CLASS_BUFFER.remove();

        // Merged in scan order, so later jars win as they did when scanned one by one
        for (Map.Entry<File, Jar> entry : jars.entrySet()) {
            Jar jar = entry.getValue();
            if (jar.forceLoadAsMod != null) {
                forceLoadAsModFiles.merge(entry.getKey().getName(), jar.forceLoadAsMod, Boolean::logicalOr);
            }
            for (Map.Entry<String, Mod> declared : jar.declared.entrySet()) {
                Mod mod = mod(declared.getKey());
                if (declared.getValue().name != null) {
                    mod.name = declared.getValue().name;
                }
                if (declared.getValue().version != null) {
                    mod.version = declared.getValue().version;
                }
            }
            for (String modId : jar.modIds) {
                mod(modId).sources.add(entry.getKey());
            }
        }

        LOGGER.info("Finished gathering {} mods, took {} ms.", mods.size(), System.currentTimeMillis() - startTime);
        LOGGER.debug("Mods gathered: {}", String.join(", ", mods.keySet()));
    }

    private static void queueScan(List<Runnable> scans, Gson gson, File file, boolean modDirCandidate) {
        File source = file.getAbsoluteFile();
        if (!source.isFile() || !source.getName().endsWith(".jar") || jars.containsKey(source)) {
            return;
        }
        Jar scanned = new Jar();
        jars.put(source, scanned);
        scans.add(() -> scanJar(gson, source, scanned, modDirCandidate));
    }

    private static void runScans(List<Runnable> scans) {
        int threads = Math.min(Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors() - 1), 1 + scans.size() / JARS_PER_THREAD);
        if (threads <= 1) {
            scans.forEach(Runnable::run);
            return;
        }
        ExecutorService executor = Executors.newFixedThreadPool(threads, runnable -> new Thread(runnable, "MixinBooter Discovery"));
        try {
            List<Future<?>> futures = new ArrayList<>(scans.size());
            for (Runnable scan : scans) {
                futures.add(executor.submit(scan));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to gather mods", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Internal usage, files with mixin config/connector entries declared in its manifest.
     */
    static List<File> manifestMixinJars() {
        List<File> manifestMixinJars = new ArrayList<>();
        for (Map.Entry<File, Jar> entry : jars.entrySet()) {
            if (entry.getValue().mixinManifest) {
                manifestMixinJars.add(entry.getKey());
            }
        }
        return manifestMixinJars;
    }

    /**
     * Internal usage, whether the given manifest mixin jar was gathered as a mods-folder
     * (or maven artifact) candidate rather than purely from the dev classloader.
     */
    static boolean isModDirMixinJar(File jar) {
        Jar scanned = jars.get(jar.getAbsoluteFile());
        return scanned != null && scanned.modDir;
    }

    /**
     * Builds the candidate jar set FML resolves in {@link CoreModManager#discoverCoreMods}.
     * The legacy candidates ({@link LibraryManager#gatherLegacyCanidates} which are from the {@code mods/} and
     * {@code mods/<version>} directories plus command-line {@code --mods}) merged with the maven artifacts
     * ({@link LibraryManager#flattenLists} which are the contained dependencies extracted into {@code memory_repo} or
     * the libraries directory). Both are read-only and already invoked by FML before this runs, so querying them
     * again here is safe.
     */
    private static List<File> gatherCandidates() {
        File mcDir = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        if ("1.12.2".equals(Environment.minecraftVersion())) {
            List<File> candidates = LibraryManager.gatherLegacyCanidates(mcDir);
            for (Artifact artifact : LibraryManager.flattenLists(mcDir)) {
                Artifact resolved = Repository.resolveAll(artifact);
                if (resolved == null) {
                    continue;
                }
                File target = resolved.getFile();
                if (target != null && !candidates.contains(target)) {
                    candidates.add(target);
                }
            }
            return candidates;
        }
        List<File> candidates = new ArrayList<>();
        File modsDir = new File(mcDir, "mods");
        if (modsDir.exists() && modsDir.isDirectory()) {
            File[] mods = modsDir.listFiles();
            if (mods != null) {
                candidates.addAll(Arrays.asList(mods));
            }
        }
        File versionDir = new File(modsDir, Environment.minecraftVersion());
        if (versionDir.exists() && versionDir.isDirectory()) {
            File[] mods = versionDir.listFiles();
            if (mods != null) {
                candidates.addAll(Arrays.asList(mods));
            }
        }
        return candidates;
    }

    private static void scanJar(Gson gson, File jar, Jar scanned, boolean modDirCandidate) {
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                // Skip Cleanroom mods
                if (ManifestAttributes.CLEANROOMMODTYPE.equals(attributes.getValue(ManifestAttributes.MODTYPE))) {
                    LOGGER.info("Skipping {} as it is a Cleanroom mod.", jar.getName());
                    return;
                }
                if (attributes.getValue(ManifestAttributes.MIXINCONFIGS) != null ||
                        attributes.getValue(ManifestAttributes.MIXINCONNECTOR) != null) {
                    scanned.mixinManifest = true;
                    scanned.modDir = modDirCandidate;
                }
                resolveLegacyBehaviour(jar, scanned, attributes, modDirCandidate);
                // OptiFine special-case
                if ("optifine.OptiFineForgeTweaker".equals(attributes.getValue(ManifestAttributes.TWEAKER))) {
                    scanned.modIds.add("optifine");
                    return;
                }
                if ("net.jan.moddirector.launchwrapper.ModDirectorTweaker".equals(attributes.getValue(ManifestAttributes.TWEAKER))) {
                    scanned.modIds.add("moddirector");
                    return;
                }
                if ("git.jbredwards.jsonpaintings.mod.asm.ASMHandler".equals(attributes.getValue(FML_CORE_PLUGIN))) {
                    scanned.modIds.add("jsonpaintings");
                    return;
                }
            }
            ZipEntry entry = jarFile.getEntry("mcmod.info");
            Set<String> modIds = new LinkedHashSet<>();
            if (entry != null) {
                parseMcmodInfo(gson, jarFile.getInputStream(entry), modIds, scanned);
            }
            // The superseded mod stops the game from launching later on, none of its code or mixins should run before that
            if (FB_SUPPORT && modIds.contains(FERMIUMBOOTER)) {
                scanned.droppedCoremod = null;
            } else {
                scanned.coremod = manifest != null && manifest.getMainAttributes().getValue(FML_CORE_PLUGIN) != null;
                if (!modIds.contains(Tags.MOD_ID)) {
                    scanClasses(jarFile, modIds, scanned);
                }
            }
            scanned.modIds.addAll(modIds);
            checkIfJarBundlesMixin(jar, jarFile, modIds);
        } catch (IOException e) {
            LOGGER.error("Failed to read mod metadata from {}", jar.getName(), e);
        }
    }

    private static Mod mod(String modId) {
        return mods.computeIfAbsent(modId, k -> new Mod());
    }

    /**
     * Logs if a mod jar bundles its own Mixin engine of any fork variety.
     */
    private static void checkIfJarBundlesMixin(File jar, JarFile jarFile, Set<String> modIds) {
        if (modIds.isEmpty() || modIds.contains("mixinbooter")) {
            return;
        }
        if (jarFile.getEntry("org/spongepowered/asm/launch/MixinBootstrap.class") != null) {
            LOGGER.warn("{} bundles its own Mixins and this may cause issues. It should depend on MixinBooter instead.", jar.getName());
        }
    }

    private static void parseMcmodInfo(Gson gson, InputStream stream, Set<String> ids, Jar scanned) {
        try {
            JsonElement root = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonElement.class);
            JsonArray mods = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().get("modList").getAsJsonArray();
            for (JsonElement element : mods) {
                if (element.isJsonObject()) {
                    JsonObject mod = element.getAsJsonObject();
                    String id = mod.get("modid").getAsString();
                    ids.add(id);
                    if (mod.has("name")) {
                        scanned.declared(id).name = mod.get("name").getAsString();
                    }
                    if (mod.has("version")) {
                        scanned.declared(id).version = mod.get("version").getAsString();
                    }
                    for (String key : new String[] { "requiredMods", "dependencies" }) {
                        if (mod.has(key) && mod.get(key).isJsonArray()) {
                            for (JsonElement dependency : mod.getAsJsonArray(key)) {
                                addDependencies(dependency.getAsString(), scanned.dependencies);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to parse mcmod.info", t);
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> getLaunchTweakClasses() {
        return (List<String>) Launch.blackboard.get(TWEAK_CLASSES);
    }

    private static void setLaunchTweakClasses(Object tweakClasses) {
        Launch.blackboard.put(TWEAK_CLASSES, tweakClasses);
        GlobalProperties.put(GlobalProperties.Keys.of(TWEAK_CLASSES), tweakClasses);
    }

    // Mods declare this in @Mod as required-after:id, after:id or even required:id, and in mcmod.info as id@range.
    // The range is not checked, some mods put the CurseForge project id there in place of a version...
    private static void addDependencies(String dependencies, Set<String> modIds) {
        if (dependencies == null) {
            return;
        }
        for (String dependency : dependencies.split("[;,]")) {
            int at = dependency.indexOf('@');
            modIds.add(dependency.substring(dependency.indexOf(':') + 1, at == -1 ? dependency.length() : at).trim());
        }
    }

    /**
     * Scans the jar's classes for {@code @Mod} annotations, adding their ids.
     * Reads bytecode only, unreadable entries and commonly shaded libraries are skipped.
     * The walk stops at the first {@code @Mod} unless the jar depends on MixinBooter, or fermiumbooter's
     * {@code @MixinConfig} classes are looked for in it.
     */
    private static void scanClasses(JarFile jar, Set<String> modIds, Jar scanned) {
        List<String> configurations = new ArrayList<>();
        byte[] buffer = CLASS_BUFFER.get();
        Enumeration<JarEntry> entries = jar.entries();
        entryLoop:
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.endsWith(".class")) {
                continue;
            }
            for (String library : SHADED_LIBRARIES) {
                if (name.startsWith(library)) {
                    continue entryLoop;
                }
            }
            try (InputStream in = jar.getInputStream(entry)) {
                if (entry.getSize() > buffer.length) {
                    buffer = new byte[(int) entry.getSize()];
                }
                int length = 0;
                for (int read; (read = in.read(buffer, length, buffer.length - length)) != -1;) {
                    length += read;
                    if (length == buffer.length) {
                        buffer = Arrays.copyOf(buffer, length << 1);
                    }
                }
                if (!namesAnnotation(buffer)) {
                    continue;
                }
                ClassScanner scanner = new ClassScanner();
                new ClassReader(buffer, 0, length).accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if (scanner.fermiumConfig) {
                    scanned.fermiumConfigs.add(name);
                }
                if (scanner.record(modIds, configurations, scanned)) {
                    addDependencies(scanner.mod.string("dependencies"), scanned.dependencies);
                    // Coremods using fermiumbooter's API do not always declare the dependency
                    boolean fermium = FB_SUPPORT && (scanned.coremod || scanned.dependencies.contains(FERMIUMBOOTER));
                    if (!scanned.dependencies.contains(Tags.MOD_ID) && !fermium) {
                        break;
                    }
                }
            } catch (Exception ignored) { }
        }
        CLASS_BUFFER.set(buffer);
        if (!configurations.isEmpty()) {
            scanned.mixinConfigurations = configurations;
        }
    }

    // Walks the constant pool alone, so ASM only parses the few classes that name an annotation being looked for
    private static boolean namesAnnotation(byte[] bytes) {
        int count = (bytes[8] & 0xFF) << 8 | bytes[9] & 0xFF;
        for (int entry = 1, offset = 10; entry < count; entry++) {
            switch (bytes[offset]) {
                case 1:
                    int length = (bytes[offset + 1] & 0xFF) << 8 | bytes[offset + 2] & 0xFF;
                    offset += 3;
                    for (byte[] annotation : ANNOTATIONS) {
                        if (length == annotation.length && regionMatches(bytes, offset, annotation)) {
                            return true;
                        }
                    }
                    offset += length;
                    break;
                case 5:
                case 6:
                    entry++;
                    offset += 9;
                    break;
                case 7:
                case 8:
                case 16:
                case 19:
                case 20:
                    offset += 3;
                    break;
                case 15:
                    offset += 4;
                    break;
                case 3:
                case 4:
                case 9:
                case 10:
                case 11:
                case 12:
                case 17:
                case 18:
                    offset += 5;
                    break;
                default:
                    return true;
            }
        }
        return false;
    }

    private static boolean regionMatches(byte[] bytes, int offset, byte[] region) {
        for (int i = 0; i < region.length; i++) {
            if (bytes[offset + i] != region[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String string) {
        return string.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Records a jar that requests Mixin's {@code ForceLoadAsMod} manifest key so it can be honored later by
     * {@link #applyForceLoadAsMod()}.
     * The actual mutation of Forge's coremod lists is deferred because this runs while
     * {@link CoreModManager#discoverCoreMods} is still populating relevant lists.
     * And if the jar has declared a Mixin bootstrap {@code TweakClass} and an {@code FMLCorePlugin},
     * the {@code TweakClass} will be cascaded and the {@code FMLCorePlugin} will never be instantiated.
     * ReplayMod uses its own bootstrap tweaker but relies on its coremod to remove the jar from Forge's ignored list.
     */
    private static void resolveLegacyBehaviour(File jar, Jar scanned, Attributes attributes, boolean modDirCandidate) {
        if ("true".equalsIgnoreCase(attributes.getValue(FORCE_LOAD_AS_MOD))) {
            scanned.forceLoadAsMod = modDirCandidate && attributes.getValue(COREMOD_CONTAINS_FML_MOD) != null;
        }
        String coremod = attributes.getValue(FML_CORE_PLUGIN);
        String tweaker = attributes.getValue(ManifestAttributes.TWEAKER);
        if (coremod != null && ALLOWED_TWEAKERS.contains(tweaker)) {
            scanned.droppedCoremod = coremod;
        }
    }

    private static final class Mod {

        private final Set<File> sources = new LinkedHashSet<>();

        private String name, version;

    }

    private static final class Jar {

        private final Set<String> modIds = new LinkedHashSet<>();
        private final Set<String> dependencies = new HashSet<>();
        // Names and versions this jar declares, merged into the mods once every jar is scanned
        private final Map<String, Mod> declared = new HashMap<>();
        private final List<String> fermiumConfigs = new ArrayList<>();

        private boolean mixinManifest, modDir, coremod;
        private Boolean forceLoadAsMod;
        private String droppedCoremod;
        private List<String> mixinConfigurations;

        private Mod declared(String modId) {
            return this.declared.computeIfAbsent(modId, k -> new Mod());
        }

    }


    private static final class CapturedTweakClasses extends ArrayList<String> {

        private final List<String> capturedTweakClasses = new ArrayList<>();

        private CapturedTweakClasses(List<String> tweakClasses) {
            super(tweakClasses == null ? Collections.emptyList() : tweakClasses);
        }

        @Override
        public boolean add(String tweakClass) {
            this.capturedTweakClasses.add(tweakClass);
            return super.add(tweakClass);
        }

        @Override
        public void add(int index, String tweakClass) {
            this.capturedTweakClasses.add(tweakClass);
            super.add(index, tweakClass);
        }

        @Override
        public boolean addAll(Collection<? extends String> tweakClasses) {
            this.capturedTweakClasses.addAll(tweakClasses);
            return super.addAll(tweakClasses);
        }

        @Override
        public boolean addAll(int index, Collection<? extends String> tweakClasses) {
            this.capturedTweakClasses.addAll(tweakClasses);
            return super.addAll(index, tweakClasses);
        }

        private List<String> getCapturedTweakClasses() {
            return this.capturedTweakClasses;
        }
    }

    /**
     * Reads a class's {@code @Mod} annotation. Annotation elements are always compile-time constants,
     * so values are read straight from the bytecode without loading the class.
     */
    private static final class ClassScanner extends ClassVisitor {

        private static final String MOD_ANNOTATION = "Lnet/minecraftforge/fml/common/Mod;";
        private static final String MIXIN_CONFIGURATION = "Lzone/rong/mixinbooter/util/MixinConfiguration;";
        private static final String FERMIUM_CONFIG = "Lfermiumbooter/annotations/MixinConfig;";

        private final FieldVisitor configurationField = new FieldVisitor(Opcodes.ASM5) {

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                ClassScanner.this.mixinConfiguration |= MIXIN_CONFIGURATION.equals(descriptor);
                return null;
            }

        };

        private String className;
        private AnnotationValues mod;
        private boolean mixinConfiguration, fermiumConfig;

        private ClassScanner() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.className = name.replace('/', '.');
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            this.fermiumConfig |= FERMIUM_CONFIG.equals(descriptor);
            return MOD_ANNOTATION.equals(descriptor) ? this.mod = new AnnotationValues() : null;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return this.configurationField;
        }

        private boolean record(Set<String> modIds, List<String> configurations, Jar scanned) {
            if (this.mixinConfiguration) {
                configurations.add(this.className);
            }
            String modId = this.mod == null ? null : this.mod.string("modid");
            if (modId != null) {
                modIds.add(modId);
                String name = this.mod.string("name"), version = this.mod.string("version");
                if (name != null) {
                    scanned.declared(modId).name = name;
                }
                if (version != null) {
                    scanned.declared(modId).version = version;
                }
            }
            return modId != null;
        }

    }

    private static final class AnnotationValues extends AnnotationVisitor {

        private final Map<String, Object> values = new HashMap<>();

        private AnnotationValues() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(String name, Object value) {
            this.values.put(name, value);
        }

        private String string(String key) {
            Object value = this.values.get(key);
            return value instanceof String && !((String) value).isEmpty() ? (String) value : null;
        }

    }

}
