package zone.rong.mixinbooter.util;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Discovers all mods present in the game directory at coremod init time,
 * independently of FML's Loader (which is not yet available). Builds a
 * bidirectional mod-id ↔ file mapping used for mixin dependency checking.
 */
public final class ModDiscoverer {

    private static final Logger LOGGER = LogManager.getLogger("MixinBooter");

    private static final SetMultimap<String, File> modIdToFiles = HashMultimap.create();
    private static final SetMultimap<File, String> fileToModIds = HashMultimap.create();
    private static boolean discovered = false;

    private ModDiscoverer() {}

    /**
     * Walks the mods directory on disk (FML-style) and supplements with any
     * classpath entries already on the LaunchClassLoader. Must be called once
     * during coremod initialisation before FML's own mod discovery runs.
     * Subsequent calls are no-ops.
     *
     * @param mcVersion Minecraft version string, used to also scan mods/&lt;version&gt;/
     */
    public static void discover(String mcVersion) {
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
        scanDirectory(gson, new File(modsDir, mcVersion));

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

        logInfo("Finished gathering %d mods...", modIdToFiles.keySet().size());
        logDebug("Mods gathered: %s", String.join(", ", modIdToFiles.keySet()));
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
        try (JarFile jf = new JarFile(jar)) {
            ZipEntry entry = jf.getEntry("mcmod.info");
            if (entry != null) {
                for (String modId : parseMcmodInfo(gson, jf.getInputStream(entry))) {
                    recordMod(modId, jar);
                }
            }
        } catch (IOException e) {
            logError("Failed to read mod metadata from %s", e, jar.getName());
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
            logError("Failed to parse mcmod.info", t);
        }
        return Collections.emptyList();
    }

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

    @SuppressWarnings("StringConcatenationArgumentToLogCall")
    private static void logInfo(String message, Object... params) {
        LOGGER.info(String.format(message, params));
    }

    @SuppressWarnings("StringConcatenationArgumentToLogCall")
    private static void logError(String message, Throwable t, Object... params) {
        LOGGER.error(String.format(message, params), t);
    }

    @SuppressWarnings("StringConcatenationArgumentToLogCall")
    private static void logDebug(String message, Object... params) {
        LOGGER.debug(String.format(message, params));
    }

}
