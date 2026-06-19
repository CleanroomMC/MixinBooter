package zone.rong.mixinbooter.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Constants.ManifestAttributes;
import zone.rong.mixinbooter.Tags;
import zone.rong.mixinbooter.util.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final SetMultimap<String, File> modIdToFiles = HashMultimap.create();
    private static final SetMultimap<File, String> fileToModIds = LinkedHashMultimap.create();
    private static final Set<File> manifestMixinJars = new HashSet<>();

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
     * Use {@link #getSourceMods(File)} instead if you need every declared id.
     *
     * @param source the jar to resolve
     * @return the owning mod's id (first declared via {@code mcmod.info}, else its {@code @Mod} annotation),
     *         or {@code null} if the jar declares no mod
     */
    public static String getSourceMod(File source) {
        Set<String> ids = getSourceMods(source);
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
    public static Set<String> getSourceMods(File source) {
        return Collections.unmodifiableSet(fileToModIds.get(source.getAbsoluteFile()));
    }

    /**
     * Walks the mods directory on disk (FML-style) and
     * supplements with classpath entries already on the LaunchClassLoader.
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

        // Primary: walk the mods directory on disk
        File modsDir = new File("mods");
        scanDirectory(gson, modsDir);
        scanDirectory(gson, new File(modsDir, Environment.minecraftVersion()));

        // Secondary: classloader URLs
        for (URL url : Launch.classLoader.getURLs()) {
            try {
                File file = new File(url.toURI());
                if (file.isFile() && file.getName().endsWith(".jar") && !fileToModIds.containsKey(file)) {
                    scanJar(gson, file);
                }
            } catch (URISyntaxException ignored) { }
        }

        // OptiFine ships no mcmod.info; detect with Config class (see: FMLClientHandler#detectOptifine)
        URL optifineUrl = Launch.classLoader.findResource("Config.class");
        if (optifineUrl != null) {
            String path = optifineUrl.getPath();
            int bangSlash = path.indexOf("!/");
            if (bangSlash >= 0) {
                try {
                    recordMod("optifine", new File(new URI(path.substring(0, bangSlash))));
                } catch (URISyntaxException ignored) { }
            }
        }

        pullManifestMixinJars();

        LOGGER.info("Finished gathering {} mods...", modIdToFiles.keySet().size());
        LOGGER.debug("Mods gathered: {}", String.join(", ", modIdToFiles.keySet()));
    }

    private static void pullManifestMixinJars() {
        if (manifestMixinJars.isEmpty()) {
            return;
        }
        Set<File> alreadyOnClassLoader = new HashSet<>();
        for (URL url : Launch.classLoader.getURLs()) {
            try {
                alreadyOnClassLoader.add(new File(url.toURI()));
            } catch (URISyntaxException | IllegalArgumentException ignored) { }
        }
        for (File jar : manifestMixinJars) {
            if (alreadyOnClassLoader.contains(jar)) {
                continue;
            }
            try {
                Launch.classLoader.addURL(jar.toURI().toURL());
                LOGGER.info("Added {} to the classloader to process its mixin manifest attributes.", jar.getName());
            } catch (Exception e) {
                LOGGER.error("Failed to add {} to the classloader to process its mixin manifest attributes.", jar.getName(), e);
            }
        }
    }

    private static void scanDirectory(Gson gson, File dir) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] jars = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
        if (jars == null) {
            return;
        }
        for (File jar : jars) {
            scanJar(gson, jar);
        }
    }

    private static void scanJar(Gson gson, File jar) {
        try (JarFile jarFile = new JarFile(jar)) {
            ZipEntry entry = jarFile.getEntry("mcmod.info");
            List<String> modIds = entry != null ? parseMcmodInfo(gson, jarFile.getInputStream(entry)) : Collections.emptyList();
            if (!modIds.isEmpty()) {
                for (String modId : modIds) {
                    recordMod(modId, jar);
                }
            } else {
                String modId = scanModAnnotation(jarFile);
                if (modId != null) {
                    recordMod(modId, jar);
                }
            }
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                if (attributes.getValue(ManifestAttributes.MIXINCONFIGS) != null || attributes.getValue(ManifestAttributes.MIXINCONNECTOR) != null) {
                    manifestMixinJars.add(jar);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read mod metadata from {}", jar.getName(), e);
        }
    }

    private static void recordMod(String modId, File source) {
        File abs = source.getAbsoluteFile();
        modIdToFiles.put(modId, abs);
        fileToModIds.put(abs, modId);
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
