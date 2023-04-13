package zone.rong.mixinextras.sugar.impl.handlers;

import zone.rong.mixinextras.injector.wrapoperation.WrapOperation;
import zone.rong.mixinextras.sugar.impl.SugarParameter;
import zone.rong.mixinextras.sugar.impl.ref.LocalRefUtils;
import zone.rong.mixinextras.utils.ASMUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Ensures all locals are captured by reference for injectors that can participate in a {@link WrapOperation} chain.
 * If a wrapper sets the local, the inner handler must receive the most up-to-date value.
 */
class LocalHandlerTransformer extends HandlerTransformer {
    private static final Set<String> TARGET_INJECTORS = new HashSet<>(Arrays.asList(
            Type.getDescriptor(ModifyConstant.class),
            Type.getDescriptor(Redirect.class),
            Type.getDescriptor(WrapOperation.class)
    ));

    LocalHandlerTransformer(IMixinInfo mixin, SugarParameter parameter) {
        super(mixin, parameter);
    }

    @Override
    public boolean isRequired(MethodNode handler) {
        AnnotationNode annotation = InjectionInfo.getInjectorAnnotation(this.mixin, handler);
        return annotation != null && TARGET_INJECTORS.contains(annotation.desc) && LocalRefUtils.getTargetType(parameter.type, parameter.genericType) == parameter.type;
    }

    @Override
    public void transform(HandlerInfo info) {
        Type wrapperType = Type.getType(LocalRefUtils.getInterfaceFor(this.parameter.type));
        info.wrapParameter(
                this.parameter,
                wrapperType,
                ASMUtils.isPrimitive(this.parameter.type) ? null : this.parameter.type,
                (insns, load) -> {
                    LocalRefUtils.generateUnwrapping(insns, this.parameter.type, load);
                }
        );
    }
}
