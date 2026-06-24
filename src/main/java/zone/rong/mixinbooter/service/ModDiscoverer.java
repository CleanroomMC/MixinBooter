package zone.rong.mixinbooter.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.libraries.Artifact;
import net.minecraftforge.fml.relauncher.libraries.LibraryManager;
import net.minecraftforge.fml.relauncher.libraries.Repository;
import org.apache.commons.io.IOUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Constants.ManifestAttributes;
import zone.rong.mixinbooter.Tags;

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

    private static final SetMultimap<String, File> modIdToFiles = HashMultimap.create();
    private static final SetMultimap<File, String> fileToModIds = LinkedHashMultimap.create();
    private static final Set<File> manifestMixinJars = new HashSet<>();
    private static final Set<String> forceLoadAsModFiles = new HashSet<>();
    private static final Set<String> forceReparseableFiles = new HashSet<>();
    private static final Map<File, String> droppedCoremods = new LinkedHashMap<>();

    private static boolean discovered = false;

    private ModDiscoverer() { }

    /**
     * Returns whether any discovered jar declares the given mod id.
     *
     * @param modId the mod id to test
     * @return {@code true} if at least one scanned jar declares the id
     */
    public static boolean isModPresent(String modId) {
        return modIdToFiles.containsKey(modId);
    }

    /**
     * Returns the ids of every mod discovered across all scanned jars.
     *
     * @return an unmodifiable view of all present mod ids
     */
    public static Set<String> getPresentMods() {
        return Collections.unmodifiableSet(modIdToFiles.keySet());
    }

    /**
     * Returns the jars that declare the given mod id.
     * A single mod id may be shipped by more than one jar (e.g. duplicate installs), hence a set.
     *
     * @param modId the mod id to resolve
     * @return the jars declaring that mod id (empty if none)
     */
    public static Set<File> getModSources(String modId) {
        return Collections.unmodifiableSet(modIdToFiles.get(modId));
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
        return Collections.unmodifiableSet(fileToModIds.get(source.getAbsoluteFile()));
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
        List<String> ignored = CoreModManager.getIgnoredMods();
        List<String> reparseable = CoreModManager.getReparseableCoremods();
        for (String name : forceLoadAsModFiles) {
            ignored.remove(name);
            LOGGER.warn("{} uses \"ForceLoadAsMod\" to be loaded as a mod from its coremod jar. This is legacy behaviour, it should be shipped as a normal mod.", name);
        }
        for (String name : forceReparseableFiles) {
            if (!reparseable.contains(name)) {
                reparseable.add(name);
            }
        }
    }

    /**
     * Loads the {@code FMLCorePlugin} of every jar that also declares a {@code TweakClass},
     * which Forge's {@link CoreModManager#discoverCoreMods} skips.
     * The removed {@code MixinPlatformAgentFMLLegacy} re-injected these via {@code CoreModManager.loadCoreMod}
     * and this replicates that. Must run from {@link CoremodsRescuer}'s constructor, a tweaker constructor being
     * the only point at which the {@code Tweaks} list is safely writeable (a rescued coremod's constructor may add
     * a tweaker to it, e.g. Sledgehammer). {@code CoremodsRescuer} is registered ahead of
     * {@code FMLInjectionAndSortingTweaker} so these wrappers are present when {@code injectCoreModTweaks} drains
     * {@code loadPlugins}, letting FML inject and sort them naturally with no manual co-initialization.
     */
    public static void rescueDroppedCoremods() {
        if (droppedCoremods.isEmpty()) {
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
        Map<String, File> coremods = new HashMap<>();
        for (Map.Entry<File, String> entry : droppedCoremods.entrySet()) {
            File jar = entry.getKey();
            String coremod = entry.getValue();
            if (coremods.containsKey(coremod)) {
                continue;
            }
            try {
                Launch.classLoader.addURL(jar.toURI().toURL());
                coremods.put(coremod, jar);
            } catch (MalformedURLException e) {
                LOGGER.error("Failed to manually load coremod {} from {}.", coremod, jar.getName(), e);
            }
        }
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
        Gson gson;
        try {
            gson = new GsonBuilder().setLenient().create();
        } catch (NoSuchMethodError e) {
            // Older Gson bundled on 1.8.x lacks setLenient()
            gson = new GsonBuilder().create();
        }

        for (File candidate : gatherCandidates()) {
            if (candidate.isFile() && candidate.getName().endsWith(".jar")) {
                scanJar(gson, candidate);
            }
        }

        // Secondary: classloader URLs
        for (URL url : Launch.classLoader.getURLs()) {
            try {
                File file = new File(url.toURI());
                if (file.isFile() && file.getName().endsWith(".jar") && !fileToModIds.containsKey(file)) {
                    scanJar(gson, file);
                }
            } catch (URISyntaxException ignored) { }
        }

        LOGGER.info("Finished gathering {} mods...", modIdToFiles.keySet().size());
        LOGGER.debug("Mods gathered: {}", String.join(", ", modIdToFiles.keySet()));
    }

    /**
     * Internal usage, files with mixin config/connector entries declared in its manifest.
     */
    static Set<File> manifestMixinJars() {
        return manifestMixinJars;
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

    private static void scanJar(Gson gson, File jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                // Skip Cleanroom mods
                if (ManifestAttributes.CLEANROOMMODTYPE.equals(attributes.getValue(ManifestAttributes.MODTYPE))) {
                    LOGGER.info("Skipping {} as it is a Cleanroom mod.", jar.getName());
                    return;
                }
                if (attributes.getValue(ManifestAttributes.MIXINCONFIGS) != null || attributes.getValue(ManifestAttributes.MIXINCONNECTOR) != null) {
                    manifestMixinJars.add(jar);
                }
                resolveLegacyBehaviour(jar, attributes);
                // OptiFine special-case
                if ("optifine.OptiFineForgeTweaker".equals(attributes.getValue(ManifestAttributes.TWEAKER))) {
                    recordMod("optifine", jar);
                    return;
                }
            }
            ZipEntry entry = jarFile.getEntry("mcmod.info");
            List<String> modIds = entry != null ? parseMcmodInfo(gson, jarFile.getInputStream(entry)) : Collections.emptyList();
            if (modIds.isEmpty()) {
                String modId = scanModAnnotation(jarFile);
                if (modId != null) {
                    modIds = Collections.singletonList(modId);
                }
            }
            for (String modId : modIds) {
                recordMod(modId, jar);
            }
            checkIfJarBundlesMixin(jar, jarFile, modIds);
        } catch (IOException e) {
            LOGGER.error("Failed to read mod metadata from {}", jar.getName(), e);
        }
    }

    private static void recordMod(String modId, File source) {
        File abs = source.getAbsoluteFile();
        modIdToFiles.put(modId, abs);
        fileToModIds.put(abs, modId);
    }

    /**
     * Logs if a mod jar bundles its own Mixin engine of any fork variety.
     */
    private static void checkIfJarBundlesMixin(File jar, JarFile jarFile, List<String> modIds) {
        if (modIds.isEmpty() || modIds.contains("mixinbooter")) {
            return;
        }
        if (jarFile.getEntry("org/spongepowered/asm/launch/MixinBootstrap.class") != null) {
            LOGGER.warn("{} bundles its own Mixins and this may cause issues. It should depend on MixinBooter instead.", jar.getName());
        }
    }

    private static List<String> parseMcmodInfo(Gson gson, InputStream stream) {
        try {
            List<String> ids = new ArrayList<>();
            JsonElement root = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), JsonElement.class);
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        ids.add(element.getAsJsonObject().get("modid").getAsString());
                    }
                }
            } else {
                for (JsonElement element : root.getAsJsonObject().get("modList").getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        ids.add(element.getAsJsonObject().get("modid").getAsString());
                    }
                }
            }
            return ids;
        } catch (Throwable t) {
            LOGGER.error("Failed to parse mcmod.info", t);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        return Collections.emptyList();
    }

    /**
     * Scans the jar's classes for the first {@code @Mod} annotation and returns its {@code modid}
     * or {@code null} if none declares one. This is the fallback for mods that declare their id via
     * the annotation rather than {@code mcmod.info}.
     * Reads bytecode only and unreadable entries are skipped, the walk stops at the first match.
     */
    private static String scanModAnnotation(JarFile jar) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                continue;
            }
            try (InputStream in = jar.getInputStream(entry)) {
                ModAnnotationVisitor visitor = new ModAnnotationVisitor();
                try {
                    new ClassReader(in).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                } catch (ExitVisitException ignored) { }
                if (visitor.modId != null) {
                    return visitor.modId;
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * Records a jar that requests Mixin's {@code ForceLoadAsMod} manifest key so it can be honored later by
     * {@link #applyForceLoadAsMod()}.
     * The actual mutation of Forge's coremod lists is deferred because this runs while
     * {@link CoreModManager#discoverCoreMods} is still populating relevant lists.
     * And if the jar has declared both a {@code TweakClass} and an {@code FMLCorePlugin}, the {@code TweakClass}
     * will be cascaded and the {@code FMLCorePlugin} will never be instantiated.
     */
    private static void resolveLegacyBehaviour(File jar, Attributes attributes) {
        if ("true".equalsIgnoreCase(attributes.getValue(FORCE_LOAD_AS_MOD))) {
            forceLoadAsModFiles.add(jar.getName());
            if (attributes.getValue(COREMOD_CONTAINS_FML_MOD) != null && !jar.getAbsolutePath().contains("deobfedDeps")) {
                forceReparseableFiles.add(jar.getName());
            }
        }
        String coremod = attributes.getValue(FML_CORE_PLUGIN);
        if (coremod != null && attributes.getValue(ManifestAttributes.TWEAKER) != null) {
            droppedCoremods.put(jar.getAbsoluteFile(), coremod);
        }
    }

    /**
     * Reads {@code modid} from a class's {@code @Mod} annotation. An annotation element is always a
     * compile-time constant, so the value is read straight from the bytecode without loading the class.
     */
    private static class ModAnnotationVisitor extends ClassVisitor {

        private static final String MOD_ANNOTATION = "Lnet/minecraftforge/fml/common/Mod;";

        private String modId;

        private ModAnnotationVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (!MOD_ANNOTATION.equals(descriptor)) {
                return null;
            }
            return new AnnotationVisitor(Opcodes.ASM5) {
                @Override
                public void visit(String name, Object value) {
                    if ("modid".equals(name) && value instanceof String) {
                        ModAnnotationVisitor.this.modId = (String) value;
                        throw new ExitVisitException();
                    }
                }
            };
        }
    }

    /**
     * Thrown to abort {@link ClassReader#accept} the moment a {@code modid} is read
     * so the rest of the class is not visited. Carries no stacktrace, for control flow and not a real exception.
     */
    private static class ExitVisitException extends RuntimeException {

        private ExitVisitException() {
            super(null, null, false, false);
        }

    }

}
