package zone.rong.mixinbooter.mixin;

import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.Proxy;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.service.MixinService;
import zone.rong.mixinbooter.Context;
import zone.rong.mixinbooter.ILateMixinLoader;
import zone.rong.mixinbooter.MixinLoader;
import zone.rong.mixinbooter.fix.MixinFixer;
import zone.rong.mixinbooter.util.ModDiscoverer;

import java.util.HashSet;
import java.util.Set;

/**
 * Mixin that allows us to load "late" mixins for mods.
 */
@Mixin(value = LoadController.class, remap = false)
public class LoadControllerMixin {

    @Shadow private Loader loader;

    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("HEAD"))
    private void beforeConstructing(LoaderState state, Object[] eventData, CallbackInfo ci) throws Throwable {
        if (state == LoaderState.CONSTRUCTING) {
            ILogger logger = MixinService.getService().getLogger("MixinBooter"); // This state is where Forge adds mod files to ModClassLoader

            ModClassLoader modClassLoader = (ModClassLoader) eventData[0];
            ASMDataTable asmDataTable = (ASMDataTable) eventData[1];

            // Add mods into the delegated ModClassLoader
            for (ModContainer container : this.loader.getActiveModList()) {
                modClassLoader.addFile(container.getSource());
            }

            // Gather ILateMixinLoaders
            Set<ASMDataTable.ASMData> interfaceData = asmDataTable.getAll(ILateMixinLoader.class.getName().replace('.', '/'));
            Set<ILateMixinLoader> lateLoaders = new HashSet<>();

            // Instantiate all @MixinLoader annotated classes
            Set<ASMDataTable.ASMData> annotatedData = asmDataTable.getAll(MixinLoader.class.getName());

            if (!annotatedData.isEmpty()) {
                for (ASMDataTable.ASMData annotated : annotatedData) {
                    try {
                        Class<?> clazz = Class.forName(annotated.getClassName());
                        logger.info("Loading annotated late loader [{}] for its mixins.", clazz.getName());
                        Object instance = clazz.newInstance();
                        if (instance instanceof ILateMixinLoader) {
                            lateLoaders.add((ILateMixinLoader) instance);
                        }
                    } catch (Throwable t) {
                        throw new RuntimeException("Unexpected error.", t);
                    }
                }
            }

            // Instantiate all ILateMixinLoader implemented classes
            if (!interfaceData.isEmpty()) {
                for (ASMDataTable.ASMData itf : interfaceData) {
                    try {
                        Class<?> clazz = Class.forName(itf.getClassName().replace('/', '.'));
                        logger.info("Loading late loader [{}] for its mixins.", clazz.getName());
                        lateLoaders.add((ILateMixinLoader) clazz.newInstance());
                    } catch (Throwable t) {
                        throw new RuntimeException("Unexpected error.", t);
                    }
                }

                for (ILateMixinLoader lateLoader : lateLoaders) {
                    try {
                        for (String mixinConfig : lateLoader.getMixinConfigs()) {
                            Context context = new Context(mixinConfig, ModDiscoverer.getPresentMods());
                            if (lateLoader.shouldMixinConfigQueue(context)) {
                                logger.info("Adding [{}] mixin configuration.", mixinConfig);
                                Mixins.addConfiguration(mixinConfig);
                                lateLoader.onMixinConfigQueued(context);
                            }
                        }
                    } catch (Throwable t) {
                        logger.error("Failed to execute late loader [{}].", lateLoader.getClass().getName(), t);
                    }
                }
            }

            // Append all unconventional mixin configurations gathered via MixinFixer
            Set<String> unconventionalConfigs = MixinFixer.retrieveLateMixinConfigs();
            if (!unconventionalConfigs.isEmpty()) {
                logger.info("Appending unconventional mixin configurations...");
                for (String unconventionalConfig : unconventionalConfigs) {
                    logger.info("Adding [{}] mixin configuration.", unconventionalConfig);
                    Mixins.addConfiguration(unconventionalConfig);
                }
            }

            Proxy.refreshMixins();
        }
    }

}
