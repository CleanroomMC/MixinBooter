package zone.rong.mixinbooter.fix.mixinextras;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;

public class MixinExtrasFixer implements IClassTransformer, Opcodes {

    @Override
    public byte[] transform(String name, String transformedName, byte[] classBytes) {
        switch (name) {
            case "com.llamalad7.mixinextras.utils.ASMUtils":
            case "com.llamalad7.mixinextras.utils.OperationUtils":
            case "com.llamalad7.mixinextras.utils.TypeUtils":
            case "com.llamalad7.mixinextras.expression.impl.utils.ExpressionASMUtils":
                return this.fixHandleInstantiation(classBytes);
        }
        return classBytes;
    }

    private byte[] fixHandleInstantiation(byte[] classBytes) {
        ClassNode node = new ClassNode();
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(node, 0);

        for (MethodNode method : node.methods) {
            ListIterator<AbstractInsnNode> iterator = method.instructions.iterator();
            while (iterator.hasNext()) {
                AbstractInsnNode instruction = iterator.next();
                if (instruction.getOpcode() == INVOKESPECIAL) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if ("org/objectweb/asm/Handle".equals(call.owner) &&
                            "<init>".equals(call.name) &&
                            "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V".equals(call.desc)) {
                        iterator.set(new InsnNode(POP));
                        iterator.add(new MethodInsnNode(
                                INVOKESPECIAL,
                                "org/objectweb/asm/Handle",
                                "<init>",
                                "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                                false)
                        );
                    }
                }
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

}
