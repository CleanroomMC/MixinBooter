package zone.rong.mixinbooter.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Constants.ManifestAttributes;
import zone.rong.mixinbooter.Tags;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * Discovers all mods present in the game directory at coremod init time,
 * independently of FML's Loader which is not available early.
 * Builds a bidirectional mod-id and file mapping used for dependency checking.
 */
public final class ModDiscoverer {

    private static final ILogger LOGGER = MixinService.getService().getLogger(Tags.MOD_NAME);
    private static final SetMultimap<String, File> modIdToFiles = HashMultimap.create();
    private static final SetMultimap<File, String> fileToModIds = HashMultimap.create();
    private static final Set<File> manifestMixinJars = new HashSet<>();

    private static boolean discovered = false;

    private ModDiscoverer() { }

    public static boolean isModPresent(String modId) {
        return modIdToFiles.containsKey(modId);
    }

    public static Set<String> getPresentMods() {
        return modIdToFiles.keySet();
    }

    public static Set<File> getModSources(String modId) {
        return Collections.unmodifiableSet(modIdToFiles.get(modId));
    }

    public static Set<String> getSourceMods(File source) {
        return Collections.unmodifiableSet(fileToModIds.get(source));
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
            if (entry != null) {
                for (String modId : parseMcmodInfo(gson, jarFile.getInputStream(entry))) {
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
        modIdToFiles.put(modId, source);
        fileToModIds.put(source, modId);
    }

    private static List<String> parseMcmodInfo(Gson gson, InputStream stream) {
        try {
            List<String> ids = new ArrayList<>();
            JsonElement root = gson.fromJson(new InputStreamReader(stream), JsonElement.class);
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
        }
        return Collections.emptyList();
    }

}
