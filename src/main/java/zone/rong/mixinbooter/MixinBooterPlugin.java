package zone.rong.mixinbooter;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.ext.Extensions;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import zone.rong.mixinextras.injector.ModifyExpressionValueInjectionInfo;
import zone.rong.mixinextras.injector.ModifyReceiverInjectionInfo;
import zone.rong.mixinextras.injector.ModifyReturnValueInjectionInfo;
import zone.rong.mixinextras.injector.WrapWithConditionInjectionInfo;
import zone.rong.mixinextras.injector.wrapoperation.WrapOperationApplicatorExtension;
import zone.rong.mixinextras.injector.wrapoperation.WrapOperationInjectionInfo;
import zone.rong.mixinextras.sugar.impl.SugarApplicatorExtension;
import zone.rong.mixinextras.sugar.impl.SugarWrapperInjectionInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

@IFMLLoadingPlugin.Name("MixinBooter")
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
public final class MixinBooterPlugin implements IFMLLoadingPlugin {

    public static final Logger LOGGER = LogManager.getLogger("MixinBooter");

    // Initialize MixinExtras
    public static void initMixinExtra(boolean runtime) {
        InjectionInfo.register(ModifyExpressionValueInjectionInfo.class);
        InjectionInfo.register(ModifyReceiverInjectionInfo.class);
        InjectionInfo.register(ModifyReturnValueInjectionInfo.class);
        InjectionInfo.register(WrapWithConditionInjectionInfo.class);
        InjectionInfo.register(WrapOperationInjectionInfo.class);
        InjectionInfo.register(SugarWrapperInjectionInfo.class);
        // Make sure it is not running in build-time
        if (runtime) {
            registerExtension(new SugarApplicatorExtension());
            registerExtension(new WrapOperationApplicatorExtension());
        }
    }

    private static Field TARGET_CLASS_CONTEXT_MIXINS_FIELD;
    private static Method MIXIN_INFO_GET_STATE_METHOD;
    private static Field STATE_CLASS_NODE_FIELD;
    private static Field INJECTION_INFO_TARGET_NODES_FIELD;
    private static Field EXTENSIONS_FIELD;
    private static Field ACTIVE_EXTENSIONS_FIELD;

    // Access to Mixin Internals, reduce callouts for Sugar implementation
    private static void bindMixinInternals() {
        try {
            Class<?> TargetClassContext = Class.forName("org.spongepowered.asm.mixin.transformer.TargetClassContext");
            TARGET_CLASS_CONTEXT_MIXINS_FIELD = TargetClassContext.getDeclaredField("mixins");
            TARGET_CLASS_CONTEXT_MIXINS_FIELD.setAccessible(true);
            Class<?> MixinInfo = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");
            MIXIN_INFO_GET_STATE_METHOD = MixinInfo.getDeclaredMethod("getState");
            MIXIN_INFO_GET_STATE_METHOD.setAccessible(true);
            Class<?> State = Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo$State");
            STATE_CLASS_NODE_FIELD = State.getDeclaredField("classNode");
            STATE_CLASS_NODE_FIELD.setAccessible(true);
            INJECTION_INFO_TARGET_NODES_FIELD = InjectionInfo.class.getDeclaredField("targetNodes");
            INJECTION_INFO_TARGET_NODES_FIELD.setAccessible(true);
            EXTENSIONS_FIELD = Extensions.class.getDeclaredField("extensions");
            EXTENSIONS_FIELD.setAccessible(true);
            ACTIVE_EXTENSIONS_FIELD = Extensions.class.getDeclaredField("activeExtensions");
            ACTIVE_EXTENSIONS_FIELD.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
            throw new RuntimeException("Failed to access some mixin internals, please inform Rongmario!", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Pair<IMixinInfo, ClassNode>> getMixinsFor(ITargetClassContext context) {
        try {
            List<Pair<IMixinInfo, ClassNode>> result = new ArrayList<>();
            SortedSet<IMixinInfo> mixins = (SortedSet<IMixinInfo>) TARGET_CLASS_CONTEXT_MIXINS_FIELD.get(context);
            for (IMixinInfo mixin : mixins) {
                Object state = MIXIN_INFO_GET_STATE_METHOD.invoke(mixin);
                ClassNode classNode = (ClassNode) STATE_CLASS_NODE_FIELD.get(state);
                result.add(Pair.of(mixin, classNode));
            }
            return result;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Failed to use mixin internals, please inform Rongmario!", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<Target, List<InjectionNodes.InjectionNode>> getTargets(InjectionInfo info) {
        try {
            return (Map<Target, List<InjectionNodes.InjectionNode>>) INJECTION_INFO_TARGET_NODES_FIELD.get(info);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to use mixin internals, please inform Rongmario!", e);
        }
    }

    // Advanced tech stuff
    @SuppressWarnings("unchecked")
    private static void registerExtension(IExtension extension) {
        IMixinTransformer transformer = (IMixinTransformer) MixinEnvironment.getDefaultEnvironment().getActiveTransformer();
        Extensions extensions = (Extensions) transformer.getExtensions();
        try {
            List<IExtension> extensionsList = (List<IExtension>) EXTENSIONS_FIELD.get(extensions);
            addExtension(extensionsList, extension);
            List<IExtension> activeExtensions = new ArrayList<>((List<IExtension>) ACTIVE_EXTENSIONS_FIELD.get(extensions));
            addExtension(activeExtensions, extension);
            ACTIVE_EXTENSIONS_FIELD.set(extensions, Collections.unmodifiableList(activeExtensions));
        } catch (IllegalAccessException e) {
            // Fail-fast so people report this and I can fix it
            throw new RuntimeException(
                String.format("Failed to inject extension %s. Please inform Rongmario!", extension),
                e
            );
        }
    }

    /**
     * This keeps the extensions in "groups", because when there are multiple relocated versions active that's the
     * behaviour we want.
     */
    private static void addExtension(List<IExtension> extensions, IExtension newExtension) {
        String extensionClassName = newExtension.getClass().getName();
        extensionClassName = extensionClassName.substring(extensionClassName.lastIndexOf('.'));
        int index = -1;
        for (int i = 0; i < extensions.size(); i++) {
            IExtension extension = extensions.get(i);
            if (extension.getClass().getName().endsWith(extensionClassName)) {
                index = i;
            }
        }
        if (index == -1) {
            extensions.add(newExtension);
        } else {
            extensions.add(index + 1, newExtension);
        }
    }

    public static String annotationToString(AnnotationNode annotation) {
        StringBuilder builder = new StringBuilder("@").append(typeToString(Type.getType(annotation.desc)));
        List<Object> values = annotation.values;
        if (values.isEmpty()) {
            return builder.toString();
        }
        builder.append('(');
        for (int i = 0; i < values.size(); i += 2) {
            if (i != 0) {
                builder.append(", ");
            }
            String name = (String) values.get(i);
            Object value = values.get(i + 1);
            builder.append(name).append(" = ").append(valueToString(value));
        }
        builder.append(')');
        return builder.toString();
    }

    public static String typeToString(Type type) {
        String name = type.getClassName();
        return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
    }

    private static String valueToString(Object value) {
        if (value instanceof String) {
            return '"' + value.toString() + '"';
        }
        if (value instanceof Type) {
            Type type = (Type) value;
            return typeToString(type) + ".class";
        }
        if (value instanceof String[]) {
            String[] enumInfo = (String[]) value;
            return typeToString(Type.getType(enumInfo[0])) + '.' + enumInfo[1];
        }
        if (value instanceof AnnotationNode) {
            return annotationToString((AnnotationNode) value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() == 1) {
                return valueToString(list.get(0));
            }
            return '{' + list.stream().map(MixinBooterPlugin::valueToString).collect(Collectors.joining(", ")) + '}';
        }
        return value.toString();
    }

    public MixinBooterPlugin() {
        LOGGER.info("MixinBootstrap Initializing...");
        MixinBootstrap.init();
        bindMixinInternals();
        initMixinExtra(true);
        Mixins.addConfiguration("mixin.mixinbooter.init.json");
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
            meta.version = "7.0";
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
