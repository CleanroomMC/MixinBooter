package zone.rong.mixinbooter;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import zone.rong.mixinextras.injector.ModifyExpressionValueInjectionInfo;
import zone.rong.mixinextras.injector.ModifyReceiverInjectionInfo;
import zone.rong.mixinextras.injector.ModifyReturnValueInjectionInfo;
import zone.rong.mixinextras.injector.WrapWithConditionInjectionInfo;
import zone.rong.mixinextras.injector.wrapoperation.WrapOperationApplicatorExtension;
import zone.rong.mixinextras.injector.wrapoperation.WrapOperationInjectionInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@IFMLLoadingPlugin.Name("MixinBooter")
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("MixinBooter");

    static {
        LOGGER.info("MixinBootstrap Initializing...");
        MixinBootstrap.init();
        // Initialize MixinExtras
        InjectionInfo.register(ModifyExpressionValueInjectionInfo.class);
        InjectionInfo.register(ModifyReceiverInjectionInfo.class);
        InjectionInfo.register(ModifyReturnValueInjectionInfo.class);
        InjectionInfo.register(WrapWithConditionInjectionInfo.class);
        InjectionInfo.register(WrapOperationInjectionInfo.class);
        // TODO: Do we need to condition this between runtime and build-time?
        registerExtension(new WrapOperationApplicatorExtension());
        Mixins.addConfiguration("mixin.mixinbooter.init.json");
    }

    // (0.1.1-rc.2) Apply @WrapOperations in an IExtension to make sure they're the last injectors to run.
    // (0.1.1-rc.4) Add extensions to Extensions#activeExtensions in case they've already been selected
    private static void registerExtension(IExtension extension) {
        IMixinTransformer transformer = (IMixinTransformer) MixinEnvironment.getDefaultEnvironment().getActiveTransformer();
        Extensions extensions = (Extensions) transformer.getExtensions();
        extensions.add(extension);
        try {
            // In case we're initialising after the extensions have already been selected, we have to hack ourselves in.
            // If we haven't passed selection yet, it doesn't matter, because the list is re-created then.
            Field activeExtensionsField = Extensions.class.getDeclaredField("activeExtensions");
            activeExtensionsField.setAccessible(true);
            List<IExtension> activeExtensions = new ArrayList<>((List<IExtension>) activeExtensionsField.get(extensions));
            activeExtensions.add(extension);
            activeExtensionsField.set(extensions, Collections.unmodifiableList(activeExtensions));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Fail-fast so people report this and I can fix it
            throw new RuntimeException(
                String.format("Failed to inject extension %s. Please inform LlamaLad7!", extension),
                e
            );
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return "zone.rong.mixinbooter.MixinBooterPlugin$Container";
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        Object coremodList = data.get("coremodList");
        if (coremodList instanceof List) {
            for (Object coremod : (List) coremodList) {
                try {
                    Field field = coremod.getClass().getField("coreModInstance");
                    field.setAccessible(true);
                    Object theMod = field.get(coremod);
                    if (theMod instanceof IEarlyMixinLoader) {
                        IEarlyMixinLoader loader = (IEarlyMixinLoader) theMod;
                        for (String mixinConfig : loader.getMixinConfigs()) {
                            if (loader.shouldMixinConfigQueue(mixinConfig)) {
                                LOGGER.info("Adding {} mixin configuration.", mixinConfig);
                                Mixins.addConfiguration(mixinConfig);
                                loader.onMixinConfigQueued(mixinConfig);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Unexpected error", e);
                }
            }
        }
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    public static class Container extends DummyModContainer {

        public Container() {
            super(new ModMetadata());
            ModMetadata meta = this.getMetadata();
            meta.modId = "mixinbooter";
            meta.name = "MixinBooter";
            meta.description = "A Mixin library and loader.";
            meta.version = "5.0";
            meta.logoFile = "/icon.png";
            meta.authorList.add("Rongmario");
        }

        @Override
        public boolean registerBus(EventBus bus, LoadController controller) {
            bus.register(this);
            return true;
        }

    }

}
