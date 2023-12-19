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
import zone.rong.mixinbooter.decorator.FMLContextQuery;
import zone.rong.mixinbooter.fix.MixinFixer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

            boolean log = false;

            // Instantiate all @MixinLoader annotated classes
            for (ASMDataTable.ASMData asmData : asmDataTable.getAll(MixinLoader.class.getName())) {
                if (!log) {
                    MixinBooterPlugin.LOGGER.info("Instantiating all MixinLoader annotated classes...");
                    log = true;
                }
                Class<?> clazz = Class.forName(asmData.getClassName());
                MixinBooterPlugin.LOGGER.info("Instantiating {} for its mixins.", clazz);
                clazz.newInstance();
            }
            log = false;

            // Instantiate all ILateMixinLoader implemented classes
            for (ASMDataTable.ASMData asmData : asmDataTable.getAll(ILateMixinLoader.class.getName().replace('.', '/'))) {
                if (!log) {
                    MixinBooterPlugin.LOGGER.info("Instantiating all ILateMixinLoader implemented classes...");
                    log = true;
                }
                Class<?> clazz = Class.forName(asmData.getClassName().replace('/', '.'));
                MixinBooterPlugin.LOGGER.info("Instantiating {} for its mixins.", clazz);
                ILateMixinLoader loader = (ILateMixinLoader) clazz.newInstance();
                for (String mixinConfig : loader.getMixinConfigs()) {
                    if (loader.shouldMixinConfigQueue(mixinConfig)) {
                        MixinBooterPlugin.LOGGER.info("Adding {} mixin configuration.", mixinConfig);
                        Mixins.addConfiguration(mixinConfig);
                        loader.onMixinConfigQueued(mixinConfig);
                    }
                }
            }
            log = false;

            // Append all non-conventional mixin configurations gathered via MixinFixer
            for (String mixinConfig : MixinFixer.retrieveLateMixinConfigs()) {
                if (!log) {
                    MixinBooterPlugin.LOGGER.info("Appending non-conventional mixin configurations...");
                    log = true;
                }
                MixinBooterPlugin.LOGGER.info("Adding {} mixin configuration.", mixinConfig);
                Mixins.addConfiguration(mixinConfig);
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
