package zone.rong.mixinbooter.loader.mixin;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.MixinProcessor;
import org.spongepowered.asm.mixin.transformer.Proxy;
import zone.rong.mixinbooter.MixinBooterPlugin;
import zone.rong.mixinbooter.MixinLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(value = LoadController.class, remap = false)
public class LoadControllerMixin {

    @Shadow private Loader loader;

    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("HEAD"))
    private void beforeConstructing(LoaderState state, Object[] eventData, CallbackInfo ci) throws Throwable {
        if (state == LoaderState.CONSTRUCTING) { // This state is where Forge adds mod files to ModClassLoader

            ModClassLoader modClassLoader = (ModClassLoader) eventData[0];
            ASMDataTable asmDataTable = (ASMDataTable) eventData[1];

            MixinBooterPlugin.LOGGER.info("Instantiating all MixinLoader annotated classes...");

            for (ASMDataTable.ASMData asmData : asmDataTable.getAll(MixinLoader.class.getName())) {
                modClassLoader.addFile(asmData.getCandidate().getModContainer()); // Add to path before `newInstance`
                Class<?> clazz = Class.forName(asmData.getClassName());
                MixinBooterPlugin.LOGGER.info("Instantiating {} for its mixins.", clazz);
                clazz.newInstance();
            }

            for (ModContainer container : this.loader.getActiveModList()) {
                modClassLoader.addFile(container.getSource());
            }

            Field transformerField = Proxy.class.getDeclaredField("transformer");
            transformerField.setAccessible(true);
            Object transformer = transformerField.get(Launch.classLoader.getTransformers().stream().filter(t -> t instanceof Proxy).findFirst().get());

            Field processorField = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);

            Method selectConfigsMethod = MixinProcessor.class.getDeclaredMethod("selectConfigs", MixinEnvironment.class);
            selectConfigsMethod.setAccessible(true);

            MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();
            selectConfigsMethod.invoke(processor, env);

            Method prepareConfigsMethod = MixinProcessor.class.getDeclaredMethod("prepareConfigs", MixinEnvironment.class);
            prepareConfigsMethod.setAccessible(true);
            prepareConfigsMethod.invoke(processor, env);
        }
    }

}
