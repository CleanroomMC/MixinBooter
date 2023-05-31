package zone.rong.mixinbooter;

import com.google.common.base.Strings;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.ModAPIManager;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Many thanks to Fabric + UniMixin's rendition of sanitizing mod ids
 */
public class ConfigDecorators {

    public static final String MIXIN_LOCATION_DECORATOR = "sourceLocation";
    public static final String MOD_ID_DECORATOR = "modId";

    private static ASMDataTable asmDataTable;

    /**
     * Prepares the ASMDataTable instance used around Forge's internal APIs.
     * ModAPIManager being the most straightforward way to gain access to the instance this early.
     */
    public static void prepareASMDataTable() {
        if (asmDataTable == null) {
            try {
                Field modApiManager$dataTable = ModAPIManager.class.getDeclaredField("dataTable");
                modApiManager$dataTable.setAccessible(true);
                asmDataTable = (ASMDataTable) modApiManager$dataTable.get(ModAPIManager.INSTANCE);
            } catch (ReflectiveOperationException e) {
                MixinBooterPlugin.LOGGER.fatal("Not able to reflect ModAPIManager#dataTable", e);
            }
        }
    }

    /**
     * Grabs the location descriptor for where the mixin was described from
     * @param mixinInfo mixin info instance
     * @return location of where the mixin and its config was described
     */
    public static String getDecoratedMixinLocation(IMixinInfo mixinInfo) {
        return getDecoratedMixinLocation(mixinInfo.getConfig(), "unknown-location");
    }

    /**
     * Grabs the location descriptor for where the mixin was described from
     * @param config mixin config instance
     * @return location of where the mixin and its config was described
     */
    public static String getDecoratedMixinLocation(IMixinConfig config) {
        return getDecoratedMixinLocation(config, "unknown-location");
    }

    /**
     * Grabs the location descriptor for where the mixin was described from
     * @param mixinInfo mixin info instance
     * @param defaultValue what will be returned if no suitable locations were found
     * @return location of where the mixin and its config was described
     */
    public static String getDecoratedMixinLocation(IMixinInfo mixinInfo, String defaultValue) {
        return getDecoratedMixinLocation(mixinInfo.getConfig(), defaultValue);
    }

    /**
     * Grabs the location descriptor for where the mixin was described from
     * @param config mixin config instance
     * @param defaultValue what will be returned if no suitable locations were found
     * @return location of where the mixin and its config was described
     */
    public static String getDecoratedMixinLocation(IMixinConfig config, String defaultValue) {
        if (config.hasDecoration(MIXIN_LOCATION_DECORATOR)) {
            String location = config.getDecoration(MIXIN_LOCATION_DECORATOR);
            return location.isEmpty() ? defaultValue : location;
        }
        String value;
        if (asmDataTable == null) {
            value = getSanitizedModIdFromResource(config);
        } else {
            value = getCandidates(config).stream()
                    .map(ModCandidate::getClassPathRoot)
                    .map(File::getName)
                    .findFirst()
                    .orElse("unknown");
        }
        config.decorate(MOD_ID_DECORATOR, value);
        return "unknown".equals(value) ? "unknown-source" : value;
    }

    /**
     * Grabs the mod id or something extremely similar of the mod that described the mixin and its config
     * @param mixinInfo mixin info instance
     * @return mod id that described the mixin and its config
     */
    public static String getDecoratedModId(IMixinInfo mixinInfo) {
        return getDecoratedModId(mixinInfo.getConfig(), "unknown");
    }

    /**
     * Grabs the mod id or something extremely similar of the mod that described the mixin and its config
     * @param config mixin config instance
     * @return mod id that described the mixin and its config
     */
    public static String getDecoratedModId(IMixinConfig config) {
        return getDecoratedModId(config, "unknown");
    }

    /**
     * Grabs the mod id or something extremely similar of the mod that described the mixin and its config
     * @param mixinInfo mixin info instance
     * @param defaultValue what will be returned if no suitable mod ids were found
     * @return mod id that described the mixin and its config
     */
    public static String getDecoratedModId(IMixinInfo mixinInfo, String defaultValue) {
        return getDecoratedModId(mixinInfo.getConfig(), defaultValue);
    }

    /**
     * Grabs the mod id or something extremely similar of the mod that described the mixin and its config
     * @param config mixin config instance
     * @param defaultValue what will be returned if no suitable mod ids were found
     * @return mod id that described the mixin and its config
     */
    public static String getDecoratedModId(IMixinConfig config, String defaultValue) {
        if (config.hasDecoration(MOD_ID_DECORATOR)) {
            String location = config.getDecoration(MOD_ID_DECORATOR);
            return location.isEmpty() ? defaultValue : location;
        }
        String value;
        if (asmDataTable == null) {
            value = getSanitizedModIdFromResource(config);
        } else {
            value = getCandidates(config).stream()
                    .map(ModCandidate::getContainedMods)
                    .flatMap(Collection::stream)
                    .map(ModContainer::getModId)
                    .filter(modId -> !Strings.isNullOrEmpty(modId))
                    .findFirst()
                    .orElseGet(() -> getSanitizedModIdFromResource(config));
        }
        config.decorate(MOD_ID_DECORATOR, value);
        return value;
    }

    private static String getSanitizedModIdFromResource(IMixinConfig config) {
        String baseModId = getResourceName(config);
        if ("unknown".equals(baseModId)) {
            return "unknown";
        }
        if (baseModId.endsWith(".jar") || baseModId.endsWith(".zip")) {
            baseModId = baseModId.substring(0, baseModId.length() - 4);
        }
        StringBuilder sanitizedModId = new StringBuilder();
        for (int i = 0; i < baseModId.length(); i++) {
            char character = baseModId.charAt(i);
            if ((character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || (i > 0 && character >= '0' && character <= '9')) {
                sanitizedModId.append(character);
            } else {
                sanitizedModId.append('_');
            }
        }
        return sanitizedModId.toString();
    }

    private static String getResourceName(IMixinConfig config) {
        String resource = Launch.classLoader.getResource(config.getName()).getPath();
        if (resource.contains("!/")) {
            String filePath = resource.split("!/")[0];
            String[] parts = filePath.split("/");
            if (parts.length != 0) {
                return parts[parts.length - 1];
            }
        }
        return "unknown";
    }

    private static Set<ModCandidate> getCandidates(IMixinConfig config) {
        String pkg = config.getMixinPackage();
        pkg = pkg.charAt(pkg.length() - 1) == '.' ? pkg.substring(0, pkg.length() - 1) : pkg;
        return asmDataTable.getCandidatesFor(pkg);
    }

    private ConfigDecorators() { }

}
