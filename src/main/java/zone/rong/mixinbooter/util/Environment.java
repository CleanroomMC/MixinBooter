package zone.rong.mixinbooter.util;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeVersion;

import java.util.List;

public class Environment {

    private static final boolean inDev = System.getProperty("sun.java.command", "").contains("GradleStart");
    private static final String side, mcVersion;

    /**
     * @return current Minecraft version
     */
    public static String minecraftVersion() {
        return mcVersion;
    }

    /**
     * @return if the current environment is in dev
     */
    public static boolean inDev() {
        return inDev;
    }

    /**
     * @return current physical side
     */
    public static String side() {
        return side;
    }

    static {
        // List<ITweaker> tweaks = GlobalProperties.get(Blackboard.TWEAKS_KEY);
        List<ITweaker> tweaks = (List<ITweaker>) Launch.blackboard.get("Tweaks");
        side = tweaks.get(0).getClass().getName().endsWith("FMLServerTweaker") ? "SERVER" : "CLIENT";

        String mcVersion0;
        try {
            // not 'mcVersion0 = ForgeVersion.mcVersion' because ForgeVersion.mcVersion is a compile-time constant and will get inlined
            mcVersion0 = (String) ForgeVersion.class.getField("mcVersion").get(null);
        } catch (Exception e) {
            // should not happen, but anyway
            switch (ForgeVersion.getMajorVersion()) {
                case 13:
                    mcVersion0 = "1.11.2";
                    break;
                case 12:
                    mcVersion0 = ForgeVersion.getMinorVersion() <= 17 ? "1.9.4" : "1.10.2";
                    break;
                case 11:
                    mcVersion0 = ForgeVersion.getBuildVersion() > 1656 ? "1.8.9" : ForgeVersion.getBuildVersion() <= 1577 ? "1.8" : "1.8.8";
                    break;
                default:
                    mcVersion0 = "1.12.2";
            }
        }
        mcVersion = mcVersion0;
    }

}
