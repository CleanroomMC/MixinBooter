package zone.rong.mixinbooter.service;

import net.minecraft.launchwrapper.Launch;
import org.spongepowered.asm.launch.GlobalProperties;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.*;
import org.spongepowered.asm.service.clean.ICleanMixinService;
import org.spongepowered.asm.service.mojang.Blackboard;
import zone.rong.mixinbooter.Tags;
import zone.rong.mixinbooter.service.platform.MixinPlatformAgent;
import zone.rong.mixinbooter.util.Environment;
import zone.rong.mixinbooter.util.LoggerAdapterLog4j2;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MixinBooterService extends MixinServiceAbstract implements ICleanMixinService {

    private static final String PROXY = MixinServiceAbstract.MIXIN_PACKAGE + "transformer.Proxy";
    private static final String STATE_TWEAKER = MixinServiceAbstract.MIXIN_PACKAGE + "EnvironmentStateTweaker";

    private final ClassProvider classProvider = new ClassProvider();
    private final TransformerProvider transformerProvider = new TransformerProvider();
    private final LaunchClassLoaderUtil classLoaderUtil = new LaunchClassLoaderUtil(Launch.classLoader);
    private final BytecodeProvider bytecodeProvider = new BytecodeProvider(transformerProvider, this.getReEntranceLock(), classLoaderUtil);

    public MixinBooterService() { }

    @Override
    public MixinEnvironment.Phase getInitialPhase() {
        if (Environment.inDev()) {
            System.setProperty("mixin.env.remapRefMap", "true");
        }
        return MixinEnvironment.Phase.PREINIT;
    }

    @Override
    public String getName() {
        return Tags.MOD_NAME;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
        return MixinEnvironment.CompatibilityLevel.JAVA_8;
    }

    @Override
    public void offer(IMixinInternal internal) {
        super.offer(internal);
        // MixinProcessor#refresh offers a "Refresh" internal when configs are (re-)selected late
        // Rebuild the delegated transformer list so any transformers registered since are picked up
        if ("Refresh".equals(internal.toString())) {
            this.transformerProvider.refreshDelegatedTransformers();
        }
    }

    @Override
    public void beginPhase() {
        Launch.classLoader.registerTransformer(PROXY);
        // The mixin transformer must never be in the delegated chain that BytecodeProvider applies
        // when fetching a target class's bytecode. Otherwise, resolving a mixin target re-enters the
        // mixin pipeline and recurses until StackOverflow.
        this.transformerProvider.addTransformerExclusion(PROXY);
    }

    @Override
    public void init() {
        GlobalProperties.<List<String>>get(Blackboard.TWEAK_CLASSES_KEY).add(STATE_TWEAKER);
        if (Environment.inDev()) {
            Mixins.addConfiguration("mixin.mixinbooter.init.json");
        }
    }

    @Override
    public IClassProvider getClassProvider() {
        return classProvider;
    }

    @Override
    public IClassBytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    @Override
    public ITransformerProvider getTransformerProvider() {
        return transformerProvider;
    }

    @Override
    public IClassTracker getClassTracker() {
        return classLoaderUtil;
    }

    @Override
    public IMixinAuditTrail getAuditTrail() {
        return null;
    }

    @Override
    public IFeatureValidator getFeatureValidator() {
        return IFeatureValidator.ALLOW_ALL;
    }

    @Override
    public IAdviceProvider getAdviceProvider() {
        return IAdviceProvider.GENERIC;
    }

    @Override
    public Collection<String> getPlatformAgents() {
        return Collections.singletonList(MixinPlatformAgent.class.getName());
    }

    @Override
    public IContainerHandle getPrimaryContainer() {
        try {
            URI uri = MixinBooterService.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            return new ContainerHandleURI(uri);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return Launch.classLoader.getResourceAsStream(name);
    }

    @Override
    protected ILogger createLogger(String name) {
        return new LoggerAdapterLog4j2(name);
    }

    @Override
    public URL getResource(String name) {
        return Launch.classLoader.findResource(name);
    }

    /**
     * A config's source id is used as its canonical mod (owner) id. {@code getCleanSourceId()} therefore will mirror
     * the mod id. Resolve the container jar to its mod id via ModDiscoverer.
     * Fallback to the jar's base name (extension and version stripped) for libraries/dev classpath dirs
     * that have no {@code mcmod.info}.
     */
    @Override
    public String getSourceId(URI source) {
        if (source == null) {
            return null;
        }
        if ("file".equals(source.getScheme())) {
            try {
                String modId = ModDiscoverer.getSourceMod(new File(source));
                if (modId != null) {
                    return modId;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        String path = source.getPath();
        if (path == null) {
            return null;
        }
        int lastSlash = path.lastIndexOf('/');
        return jarBaseName(lastSlash >= 0 ? path.substring(lastSlash + 1) : path);
    }

    /**
     * Best-effort owner id for a container that declares no mod: the file name with the {@code .jar}
     * extension and trailing {@code -version} suffix stripped. {@code getCleanSourceId()} ({@link
     * org.spongepowered.asm.mixin.extensibility.IMixinConfig#cleanId}) will drop everything else that
     * is not alphanumeric (sort prefixes like {@code !}, bracketed/CJK name prefixes, separators).
     */
    private static String jarBaseName(String name) {
        if (name.endsWith(".jar")) {
            name = name.substring(0, name.length() - ".jar".length());
        }
        // Strip the trailing versioning
        int i = name.length();
        while (i > 0 && (Character.isDigit(name.charAt(i - 1)) || name.charAt(i - 1) == '.')) {
            i--;
        }
        // How...?
        if (i == 0 || i == name.length()) {
            return name;
        }
        // Drop separator if the jar is conventionally named
        if (name.charAt(i - 1) == '-') {
            i--;
        }
        return name.substring(0, i);
    }

}
