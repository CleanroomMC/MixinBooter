package zone.rong.mixinbooter;

import net.minecraftforge.common.config.Configuration;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.transformer.Config;
import org.spongepowered.asm.service.MixinService;
import zone.rong.mixinbooter.service.ClassLoadTracer;
import zone.rong.mixinbooter.service.MixinBooterService;

import java.io.File;

/**
 * Reads {@code config/mixinbooter.cfg} during the earliest coremod boot and applies its options.
 */
public final class MixinBooterConfig {

    private static final String CATEGORY_DEBUG = "debug";

    private MixinBooterConfig() { }

    /**
     * Load the config file and apply the options that must take effect early.
     */
    static void load() {
        ILogger logger = MixinService.getService().getLogger(Tags.MOD_NAME);
        try {
            Configuration config = new Configuration(new File("config", Tags.MOD_ID + ".cfg"));
            config.load();

            String[] blacklistedConfigs = config.getStringList("blacklistedConfigs", Configuration.CATEGORY_GENERAL,
                    new String[0],
                    "Mixin configurations that should never be loaded.\n" +
                    "Use this to forcibly disable a broken or unwanted mixin config shipped by any mod.");
            for (String configName : blacklistedConfigs) {
                if (configName != null) {
                    configName = configName.trim();
                    if (!configName.isEmpty()) {
                        logger.info("Blacklisting {} as requested by the user's configuration", configName);
                        Config.blacklist(configName);
                    }
                }
            }

            boolean auditTrail = config.getBoolean("auditTrail", Configuration.CATEGORY_GENERAL, true,
                    "Mirror mixin activity into logs/mixinbooter.log. Disable to skip writing that file entirely.");
            if (!auditTrail) {
                System.setProperty(MixinBooterService.AUDIT_PROPERTY, "false");
            }

            applyFlag(config, "verbose", "mixin.debug.verbose",
                    "Enable verbose mixin logging (Equivalent to: -Dmixin.debug.verbose=true).");
            applyFlag(config, "export", "mixin.debug.export",
                    "Export transformed classes to the .mixin.out directory (Equivalent to: -Dmixin.debug.export=true).");
            applyFlag(config, "checkInterfaces", "mixin.checks.interfaces",
                    "Verify that mixins implement every method of the interfaces they declare (Equivalent to: -Dmixin.checks.interfaces=true).");

            String[] watchedClasses = config.getStringList("watchedClasses", CATEGORY_DEBUG, new String[0],
                    "Fully-qualified class names to trace at load time. When one of these classes is loaded, the\n" +
                    "call stack that triggered the load is written to logs/mixinbooter.log. Use this to diagnose\n" +
                    "'mixin target was loaded too early' problems by finding which code class-loaded the target.\n" +
                    "Use a single '*' to trace (near) every class load (very verbose). Leave empty to disable.\n" +
                    "Example: net.minecraft.client.Minecraft");
            StringBuilder watched = new StringBuilder();
            String existing = System.getProperty(ClassLoadTracer.WATCH_PROPERTY);
            if (existing != null && !existing.trim().isEmpty()) {
                watched.append(existing.trim());
            }
            for (String watchedClass : watchedClasses) {
                if (watchedClass != null) {
                    watchedClass = watchedClass.trim();
                    if (!watchedClass.isEmpty()) {
                        if (watched.length() > 0) {
                            watched.append(',');
                        }
                        watched.append(watchedClass);
                    }
                }
            }
            if (watched.length() > 0) {
                System.setProperty(ClassLoadTracer.WATCH_PROPERTY, watched.toString());
            }

            if (config.hasChanged()) {
                config.save();
            }
        } catch (Throwable t) {
            logger.error("Error loading config", t);
        }
    }

    private static void applyFlag(Configuration config, String key, String property, String comment) {
        boolean value = config.getBoolean(key, CATEGORY_DEBUG, false, comment);
        if (value && System.getProperty(property) == null) {
            System.setProperty(property, "true");
        }
    }

}
