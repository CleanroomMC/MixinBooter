package zone.rong.mixinextras.sugar.impl;

import zone.rong.mixinextras.sugar.impl.ref.LocalRefClassGenerator;
import zone.rong.mixinextras.sugar.impl.ref.LocalRefUtils;
import zone.rong.mixinextras.utils.Decorations;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.injection.modify.InvalidImplicitDiscriminatorException;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator;
import org.spongepowered.asm.mixin.injection.modify.LocalVariableDiscriminator.Context;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode;
import org.spongepowered.asm.mixin.injection.struct.Target;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.asm.util.SignaturePrinter;

import java.util.HashMap;
import java.util.Map;

class LocalSugarApplicator extends SugarApplicator {
    private final boolean isArgsOnly;
    private final Type targetLocalType = LocalRefUtils.getTargetType(this.paramType, this.paramGeneric);
    private final boolean isMutable = targetLocalType != paramType;

    LocalSugarApplicator(InjectionInfo info, SugarParameter parameter) {
        super(info, parameter);
        this.isArgsOnly = Annotations.getValue(sugar, "argsOnly", (Boolean) false);
    }

    @Override
    void validate(Target target, InjectionNode node) {
        LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(sugar);
        Context context = getOrCreateLocalContext(target, node);
        if (discriminator.printLVT()) {
            printLocals(target, node.getCurrentTarget(), context, discriminator);
            info.addCallbackInvocation(info.getMethod());
            throw new SugarApplicationException("Application aborted because locals are being printed instead.");
        }
        try {
            if (discriminator.findLocal(context) < 0) {
                throw new SugarApplicationException("Unable to find matching local!");
            }
        } catch (InvalidImplicitDiscriminatorException e) {
            throw new SugarApplicationException("Invalid implicit variable discriminator: ", e);
        }
    }

    @Override
    void prepare(Target target, InjectionNode node) {
        getOrCreateLocalContext(target, node);
    }

    @Override
    void inject(Target target, InjectionNode node) {
        LocalVariableDiscriminator discriminator = LocalVariableDiscriminator.parse(sugar);
        Context context = node.getDecoration(getLocalContextKey());
        int index = discriminator.findLocal(context);
        if (index < 0) {
            throw new SugarApplicationException("Failed to match a local, this should have been caught during validation.");
        }
        if (isMutable) {
            initAndLoadLocalRef(target, node, index);
        } else {
            target.insns.insertBefore(node.getCurrentTarget(), new VarInsnNode(targetLocalType.getOpcode(Opcodes.ILOAD), index));
        }
    }

    private void initAndLoadLocalRef(Target target, InjectionNode node, int index) {
        String refName = LocalRefClassGenerator.getForType(targetLocalType);
        int refIndex = getOrCreateRef(target, node, index, refName);
        target.insns.insertBefore(node.getCurrentTarget(), new VarInsnNode(Opcodes.ALOAD, refIndex));
    }

    private int getOrCreateRef(Target target, InjectionNode node, int index, String refImpl) {
        Map<Integer, Integer> refIndices = node.getDecoration(Decorations.LOCAL_REF_MAP);
        if (refIndices == null) {
            refIndices = new HashMap<>();
            node.decorate(Decorations.LOCAL_REF_MAP, refIndices);
        }
        if (refIndices.containsKey(index)) {
            // Another handler has already created an applicable reference, so we should share it.
            return refIndices.get(index);
        }
        // We have to create the reference ourselves as we are the first to need it.
        int refIndex = target.allocateLocal();
        target.addLocalVariable(refIndex, "ref" + refIndex, 'L' + refImpl + ';');
        // Make and store the reference object with the local's current value.
        InsnList before = new InsnList();
        LocalRefUtils.generateWrapping(
                before, targetLocalType,
                () -> before.add(new VarInsnNode(targetLocalType.getOpcode(Opcodes.ILOAD), index))
        );
        before.add(new VarInsnNode(Opcodes.ASTORE, refIndex));
        target.insertBefore(node, before);
        // Write the reference's value back to its target local after the handler call.
        InsnList after = new InsnList();
        LocalRefUtils.generateUnwrapping(
                after, targetLocalType,
                () -> after.add(new VarInsnNode(Opcodes.ALOAD, refIndex))
        );
        after.add(new VarInsnNode(targetLocalType.getOpcode(Opcodes.ISTORE), index));
        target.insns.insert(node.getCurrentTarget(), after);
        // Tell future injectors where to find the reference.
        refIndices.put(index, refIndex);
        return refIndex;
    }

    private Context getOrCreateLocalContext(Target target, InjectionNode node) {
        String decorationKey = getLocalContextKey();
        if (node.hasDecoration(decorationKey)) {
            return node.getDecoration(decorationKey);
        }
        Context context = new Context(targetLocalType, isArgsOnly, target, node.getCurrentTarget());
        node.decorate(decorationKey, context);
        return context;
    }

    private String getLocalContextKey() {
        return String.format("mixinextras_localSugarContext(%s,%s)", targetLocalType, isArgsOnly ? "argsOnly" : "fullFrame");
    }

    private void printLocals(Target target, AbstractInsnNode node, Context context, LocalVariableDiscriminator discriminator) {
        int baseArgIndex = target.isStatic ? 0 : 1;

        new PrettyPrinter()
                .kvWidth(20)
                .kv("Target Class", target.classNode.name.replace('/', '.'))
                .kv("Target Method", target.method.name)
                .kv("Capture Type", SignaturePrinter.getTypeName(targetLocalType, false))
                .kv("Instruction", "[%d] %s %s", target.insns.indexOf(node), node.getClass().getSimpleName(),
                        Bytecode.getOpcodeName(node.getOpcode())).hr()
                .kv("Match mode", isImplicit(discriminator, baseArgIndex) ? "IMPLICIT (match single)" : "EXPLICIT (match by criteria)")
                .kv("Match ordinal", discriminator.getOrdinal() < 0 ? "any" : discriminator.getOrdinal())
                .kv("Match index", discriminator.getIndex() < baseArgIndex ? "any" : discriminator.getIndex())
                .kv("Match name(s)", discriminator.hasNames() ? discriminator.getNames() : "any")
                .kv("Args only", isArgsOnly).hr()
                .add(context)
                .print(System.err);
    }

    private boolean isImplicit(LocalVariableDiscriminator discriminator, int baseArgIndex) {
        return discriminator.getOrdinal() < 0 && discriminator.getIndex() < baseArgIndex && discriminator.getNames().isEmpty();
    }
}
