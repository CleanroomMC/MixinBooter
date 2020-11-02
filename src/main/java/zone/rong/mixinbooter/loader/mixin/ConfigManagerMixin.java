package zone.rong.mixinbooter.loader.mixin;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.MixinProcessor;
import org.spongepowered.asm.mixin.transformer.Proxy;
import zone.rong.mixinbooter.MixinBooterPlugin;
import zone.rong.mixinbooter.MixinLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(ConfigManager.class)
public class ConfigManagerMixin {

    @Inject(method = "loadData", at = @At("TAIL"), remap = false)
    private static void processMixinLoaders(ASMDataTable data, CallbackInfo ci) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        MixinBooterPlugin.LOGGER.info("Instantiating all MixinLoader annotated classes...");
        for (ASMDataTable.ASMData asmData : data.getAll(MixinLoader.class.getName())) {
            Class.forName(asmData.getClassName()).newInstance();
        }
        MixinBooterPlugin.LOGGER.info("Preparing newly added mixin configs...");
        try {
            Class<?> mixinTransformerClass = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");

            Field transformerField = Proxy.class.getDeclaredField("transformer");
            transformerField.setAccessible(true);
            Object transformer = transformerField.get(Launch.classLoader.getTransformers().stream().filter(t -> t instanceof Proxy).findFirst().get());

            Field processorField = mixinTransformerClass.getDeclaredField("processor");
            processorField.setAccessible(true);
            Object processor = processorField.get(transformer);

            final MixinEnvironment env = MixinEnvironment.getCurrentEnvironment();

            Method selectConfigsMethod = MixinProcessor.class.getDeclaredMethod("selectConfigs", MixinEnvironment.class);
            selectConfigsMethod.setAccessible(true);
            selectConfigsMethod.invoke(processor, env);

            Method prepareConfigsMethod = MixinProcessor.class.getDeclaredMethod("prepareConfigs", MixinEnvironment.class);
            prepareConfigsMethod.setAccessible(true);
            prepareConfigsMethod.invoke(processor, env);

        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

}
