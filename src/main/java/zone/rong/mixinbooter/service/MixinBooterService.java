package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.obfuscation.mapping.remap.CleanroomRemapper;
import org.spongepowered.asm.service.mojang.AbstractMixinServiceLaunchWrapper;
import org.spongepowered.asm.service.mojang.MixinAuditFile;
import zone.rong.mixinbooter.Tags;
import zone.rong.mixinbooter.util.Environment;
import zone.rong.mixinbooter.util.Srg2NotchRemapper;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MixinBooterService extends AbstractMixinServiceLaunchWrapper {

    public static final String AUDIT_PROPERTY = Tags.MOD_ID + ".auditTrail";

    private static final MixinAuditFile AUDIT_FILE = new MixinAuditFile(Tags.MOD_ID + ".log", AUDIT_PROPERTY);

    private boolean initialized;

    /** The shared mixin log, also written to by {@link ClassLoadTracer} and the teeing {@link org.spongepowered.asm.service.mojang.Log4j2AuditingAdapter}. */
    public static MixinAuditFile auditFile() {
        return AUDIT_FILE;
    }

    @Override
    public String getName() {
        return Tags.MOD_NAME;
    }

    @Override
    protected boolean isDevelopment() {
        return Environment.inDev();
    }

    @Override
    public String getSideName() {
        return Environment.side();
    }

    @Override
    protected MixinAuditFile createAuditLog() {
        return AUDIT_FILE;
    }

    @Override
    public void beginPhase() {
        this.getTransformerProvider().addTransformerExclusion("zone.rong.mixinbooter.service.ClassLoadTracer");
        super.beginPhase();
    }

    @Override
    public void init() {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        super.init();
        MixinEnvironment.getDefaultEnvironment().getRemappers().add(new CleanroomRemapper<>(new Srg2NotchRemapper()));
        if (Environment.inDev()) { // RFG
            Mixins.addConfiguration("mixin.mixinbooter.init.json");
        }
    }

    @Override
    public Collection<IContainerHandle> getMixinContainers() {
        List<IContainerHandle> containers = new ArrayList<>();
        Set<File> jars = ModDiscoverer.manifestMixinJars();
        if (jars.isEmpty()) {
            return containers;
        }
        ILogger logger = getLogger(Tags.MOD_NAME);
        Set<String> existingJars = new HashSet<>(CoreModManager.getIgnoredMods());
        existingJars.addAll(CoreModManager.getReparseableCoremods());
        for (File jar : jars) {
            containers.add(new ContainerHandleURI(jar.toURI()));
            if (existingJars.contains(jar.getName())) {
                continue;
            }
            try {
                Launch.classLoader.addURL(jar.toURI().toURL());
                logger.info("Added {} to the classloader to process its mixin manifest attributes.", jar.getName());
            } catch (Exception e) {
                logger.error("Failed to add {} to the classloader to process its mixin manifest attributes.", jar.getName(), e);
            }
        }
        return containers;
    }

    @Override
    protected String resolveSourceId(URI source) {
        if ("file".equals(source.getScheme())) {
            try {
                return ModDiscoverer.getModFromSource(new File(source));
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
    }

}
