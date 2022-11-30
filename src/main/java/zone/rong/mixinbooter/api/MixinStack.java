package zone.rong.mixinbooter.api;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraftforge.fml.relauncher.CoreModManager;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import zone.rong.mixinbooter.MixinBooterPlugin;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Collectors;

public class MixinStack {
    private static String reflectionErrors = "";
    private static Method classInfo$getMixins;
    private static Field coreModManager$loadPlugins;
    private static Field coreModManager$coreModInstance;
    private static Field annotationInvocationHandler$memberValues;
    private String errorMessage;
    private String stackMessage;

    private Map<String, Set<IMixinInfo>> mixinStack;
    private Collection<IMixinInfo> flatMixins;

    static {
        StringBuilder reflectionErrorsBuilder = new StringBuilder();

        try {
            classInfo$getMixins = ClassInfo.class.getDeclaredMethod("getMixins");
            classInfo$getMixins.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            reflectionErrorsBuilder.append("\nNot able to reflect ClassInfo#getMixins\n");
            MixinBooterPlugin.LOGGER.error("Not able to reflect ClassInfo#getMixins");
        }

        try {
            coreModManager$loadPlugins = CoreModManager.class.getDeclaredField("loadPlugins");
            coreModManager$loadPlugins.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            reflectionErrorsBuilder.append("\nNot able to reflect coreModManager#loadPlugins\n");
            MixinBooterPlugin.LOGGER.error("Not able to reflect coreModManager#loadPlugins");
        }

        try {
            coreModManager$coreModInstance = CoreModManager.class.getDeclaredClasses()[0].getDeclaredField("coreModInstance");
            coreModManager$coreModInstance.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            reflectionErrorsBuilder.append("\nNot able to reflect FMLPluginWrapper#coreModInstance\n");
            MixinBooterPlugin.LOGGER.error("Not able to reflect FMLPluginWrapper#coreModInstance");
        }

        try {
            //this is used since im not able to get method.getAnnotation(MixinMessage.class) to work
            annotationInvocationHandler$memberValues = Class.forName("sun.reflect.annotation.AnnotationInvocationHandler").getDeclaredField("memberValues");
            annotationInvocationHandler$memberValues.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            reflectionErrorsBuilder.append("\nNot able to reflect annotationInvocationHandler#memberValues\n");
            MixinBooterPlugin.LOGGER.error("Not able to reflect annotationInvocationHandler#memberValues");
        }

        if (reflectionErrorsBuilder.length() != 0) {
            reflectionErrorsBuilder.insert(0, "\n(MixinBooter) errors while reflecting:");
            reflectionErrors = reflectionErrorsBuilder.toString();
        }
    }

    private static final Set<IMixinLogGenerator> iLateMixinsLogRegisteredCallbacks = new HashSet<>();

    public static void registerILateMixinsLogCallback(IMixinLogGenerator callback) {
        iLateMixinsLogRegisteredCallbacks.add(callback);
    }


    public static MixinStack createStackReport(StackTraceElement[] stacktrace) {
        return new MixinStack(stacktrace);
    }

    public static MixinStack createStackReport(Throwable throwable) {
        return new MixinStack(throwable.getStackTrace());
    }

    private MixinStack(StackTraceElement[] stacktrace) {
        if (reflectionErrors.length() != 0) {
            return;
        }

        if (stacktrace.length == 0) {
            errorMessage = "\n(MixinBooter) Failed to find Mixin Metadata because the provided Stacktrace is empty\n";
            return;
        }

        mixinStack = new HashMap<>();
        Set<String> classes = new ObjectOpenHashSet<>();
        for (StackTraceElement stackTraceElement : stacktrace) {
            classes.add(stackTraceElement.getClassName());
        }
        try {
            for (String className : classes) {
                ClassInfo classInfo = ClassInfo.fromCache(className);
                if (classInfo != null) {
                    @SuppressWarnings("unchecked")
                    Set<IMixinInfo> mixinInfos = (Set<IMixinInfo>) classInfo$getMixins.invoke(classInfo);
                    if (!mixinInfos.isEmpty()) {
                        mixinStack.putIfAbsent(className, mixinInfos);
                    }
                }
            }

            if (mixinStack.size() == 0) {
                errorMessage = "\n(MixinBooter) No Mixin Metadata is found in the Stacktrace.\n";
            }

        } catch (Throwable t) {
            errorMessage = "\n(MixinBooter) Failed to find Mixin Metadata in Stacktrace: " + t + "\n";
        }
    }


    private final StringBuilder tabBuilder = new StringBuilder();

    private String indentBuilder(int indent) {
        String tab;
        for (int i = 0; i < indent; i++) {
            tabBuilder.append("\t");
        }
        tab = tabBuilder.toString();
        tabBuilder.setLength(0);
        return tab;
    }

    private void formatStackTraceMixins(StringBuilder mixinMetadataBuilder, int indent, String mixinClass) {
        mixinMetadataBuilder.append("\n");
        mixinMetadataBuilder.append(indentBuilder(indent));
        mixinMetadataBuilder.append(mixinClass);
        mixinMetadataBuilder.append(":");
    }

    private void formatStackTraceMixins(StringBuilder mixinMetadataBuilder, int indent, IMixinInfo mixinInfo) {
        mixinMetadataBuilder.append("\n");
        mixinMetadataBuilder.append(indentBuilder(indent));
        mixinMetadataBuilder.append(mixinInfo.getClassName());
        mixinMetadataBuilder.append(" (");
        mixinMetadataBuilder.append(mixinInfo.getConfig().getName());
        mixinMetadataBuilder.append(")");
    }

    private boolean formatCustomModStackMessages(StringBuilder mixinMessageBuilder, int indent, String mixinJson, IMixinLogGenerator callback) {
        if (callback.shouldMixinReportCustomMessage(mixinJson)) {
            mixinMessageBuilder.append("\n");
            mixinMessageBuilder.append(indentBuilder(indent));
            mixinMessageBuilder.append(callback.onMixinMessage(mixinJson));
            return true;
        }
        return false;
    }

    private void formatCustomModStackAnnotationMessages(StringBuilder mixinMessageBuilder, int indent, IMixinInfo mixinInfo) {
        try {
            Class<?> clazz = Class.forName(mixinInfo.getClassName(), false, ClassLoader.getSystemClassLoader());
            for (Method method : clazz.getMethods()) {
                for (Annotation annotation : method.getDeclaredAnnotations()) {
                    if (annotation.annotationType().getName().equals(MixinMessage.class.getName())) {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Map<String, Object> annotationMap = (Map) annotationInvocationHandler$memberValues.get(Proxy.getInvocationHandler(annotation));
                        for (Map.Entry<String, Object> mapEntry : annotationMap.entrySet()) {
                            mixinMessageBuilder.append("\n");
                            mixinMessageBuilder.append(indentBuilder(indent));
                            mixinMessageBuilder.append(method.getName());
                            mixinMessageBuilder.append(" : ");
                            mixinMessageBuilder.append(mapEntry.getKey());
                            mixinMessageBuilder.append(" : ");
                            mixinMessageBuilder.append(mapEntry.getValue());
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            mixinMessageBuilder.append("\n");
            mixinMessageBuilder.append(indentBuilder(indent));
            mixinMessageBuilder.append("Unexpected error while generating custom mixin messages");

            MixinBooterPlugin.LOGGER.error("Unexpected error while generating custom mixin messages: " + e);
        }
    }

    private Set<IMixinLogGenerator> findCallbacks(Collection<IMixinInfo> mixinInfoCollection) {
        Set<IMixinLogGenerator> iMixinLogGeneratorCallback = new HashSet<>();

        try {
            @SuppressWarnings("rawtypes")
            List loadPlugins = (List) coreModManager$loadPlugins.get(this);
            for (Object coreModWrapper : loadPlugins) {
                Object coreMod = coreModManager$coreModInstance.get(coreModWrapper);

                if (coreMod instanceof IMixinLogGenerator) {
                    IMixinLogGenerator coreModLogger = (IMixinLogGenerator) coreMod;

                    for (IMixinInfo mixins : mixinInfoCollection) {
                        for (String mixinJson : coreModLogger.getMixinConfigs()) {
                            if (mixins.getConfig().getName().equals(mixinJson)) {
                                iMixinLogGeneratorCallback.add(coreModLogger);
                            }
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            MixinBooterPlugin.LOGGER.error("Unexpected error while generating custom mixin messages: " + e);
        }

        for (IMixinLogGenerator logGenerator : iLateMixinsLogRegisteredCallbacks) {
            for (IMixinInfo mixins : mixinInfoCollection) {
                for (String mixinJson : logGenerator.getMixinConfigs()) {
                    if (mixins.getConfig().getName().equals(mixinJson)) {
                        iMixinLogGeneratorCallback.add(logGenerator);
                    }
                }
            }
        }

        return iMixinLogGeneratorCallback;
    }

    private Collection<IMixinInfo> getFlatMixins() {
        if (flatMixins == null) {
            flatMixins = mixinStack.values().stream().flatMap(Collection::stream).collect(Collectors.toList());
        }
        return flatMixins;
    }

    private boolean formatStackMessage() {
        if (errorMessage != null || reflectionErrors != null) {
            return false;
        }

        if (stackMessage != null) {
            return true;
        }

        StringBuilder mixinLogBuilder = new StringBuilder();
        Set<IMixinLogGenerator> callbacks = findCallbacks(getFlatMixins());

        if (callbacks.isEmpty()) {
            for (Map.Entry<String, Set<IMixinInfo>> stack : mixinStack.entrySet()) {
                formatStackTraceMixins(mixinLogBuilder, 1, stack.getKey());

                for (IMixinInfo mixin : stack.getValue()) {
                    formatStackTraceMixins(mixinLogBuilder, 2, mixin);
                }
            }
        } else {
            for (Map.Entry<String, Set<IMixinInfo>> stack : mixinStack.entrySet()) {
                formatStackTraceMixins(mixinLogBuilder, 1, stack.getKey());

                for (IMixinInfo mixin : stack.getValue()) {
                    for (IMixinLogGenerator callback : callbacks) {
                        if (formatCustomModStackMessages(mixinLogBuilder, 2, mixin.getConfig().getName(), callback)) {
                            formatStackTraceMixins(mixinLogBuilder, 3, mixin);
                            formatCustomModStackAnnotationMessages(mixinLogBuilder, 4, mixin);
                            continue;
                        }

                        formatStackTraceMixins(mixinLogBuilder, 2, mixin);
                        formatCustomModStackAnnotationMessages(mixinLogBuilder, 3, mixin);
                    }
                }
            }
        }

        if (mixinLogBuilder.length() != 0) {
            mixinLogBuilder.insert(0, "\n(MixinBooter) Mixins in Stacktrace:");
        }

        stackMessage = mixinLogBuilder.toString();
        return true;
    }

    public boolean isStackMessagePresent() {
        return formatStackMessage();
    }

    public String getStackMessage() {
        if (isStackMessagePresent()) {
            return stackMessage;
        }
        if (errorMessage == null) {
            return reflectionErrors;
        }
        return errorMessage;
    }

    public boolean isMixinStackPresent() {
        return mixinStack != null;
    }

    public boolean isMixinPartOfStackTrace(String mixinJson) {
        if (isMixinStackPresent()) {
            return getFlatMixins().stream().
                    anyMatch(iMixinInfo -> iMixinInfo.getConfig().getName().equals(mixinJson));
        }

        return false;
    }

}
