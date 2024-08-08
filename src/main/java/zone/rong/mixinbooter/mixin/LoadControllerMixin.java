package zone.rong.mixinbooter.mixin;

import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.extensibility.IMixinProcessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.Proxy;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.service.mojang.MixinServiceLaunchWrapper;
import zone.rong.mixinbooter.*;
import zone.rong.mixinbooter.fix.MixinFixer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
        if (state == LoaderState.CONSTRUCTING) { // This state is where Forge adds mod files to ModClassLoader

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
                    Class<?> clazz = Class.forName(annotated.getClassName());
                    MixinBooterPlugin.LOGGER.info("Instantiating @MixinLoader annotated class: " + clazz);
                    Object instance = clazz.newInstance();
                    if (instance instanceof ILateMixinLoader) {
                        lateLoaders.add((ILateMixinLoader) instance);
                    }
                }
            }

            // Instantiate all ILateMixinLoader implemented classes
            if (!interfaceData.isEmpty()) {
                for (ASMDataTable.ASMData itf : interfaceData) {
                    Class<?> clazz = Class.forName(itf.getClassName().replace('/', '.'));
                    MixinBooterPlugin.LOGGER.info("Instantiating ILateMixinLoader class: " + clazz);
                    lateLoaders.add((ILateMixinLoader) clazz.newInstance());
                }
                for (ILateMixinLoader lateLoader : lateLoaders) {
                    for (String mixinConfig : lateLoader.getMixinConfigs()) {
                        if (lateLoader.shouldMixinConfigQueue(mixinConfig)) {
                            MixinBooterPlugin.LOGGER.info("Adding " + mixinConfig + " mixin configuration.");
                            Mixins.addConfiguration(mixinConfig);
                            lateLoader.onMixinConfigQueued(mixinConfig);
                        }
                    }
                }
            }

            // Append all unconventional mixin configurations gathered via MixinFixer
            Set<String> unconventionalConfigs = MixinFixer.retrieveLateMixinConfigs();
            if (!unconventionalConfigs.isEmpty()) {
                MixinBooterPlugin.LOGGER.info("Appending unconventional mixin configurations...");
                for (String unconventionalConfig : unconventionalConfigs) {
                    MixinBooterPlugin.LOGGER.info("Adding " + unconventionalConfig + " mixin configuration.");
                    Mixins.addConfiguration(unconventionalConfig);
                }
            }

            // Rebuild delegated transformers
            Field delegatedTransformersField = MixinServiceLaunchWrapper.class.getDeclaredField("delegatedTransformers");
            delegatedTransformersField.setAccessible(true);
            delegatedTransformersField.set(MixinService.getService(), null);

            IMixinProcessor processor = ((IMixinTransformer) Proxy.transformer).getProcessor();
            Method selectMethod = processor.getClass().getDeclaredMethod("select", MixinEnvironment.class);
            selectMethod.setAccessible(true);
            selectMethod.invoke(processor, MixinEnvironment.getCurrentEnvironment());
        }
    }

}
