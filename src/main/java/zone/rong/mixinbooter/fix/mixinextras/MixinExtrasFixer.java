package zone.rong.mixinbooter.fix.mixinextras;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ListIterator;

public class MixinExtrasFixer implements IClassTransformer, Opcodes {

    public static Handle redirect(int tag, String owner, String name, String desc, boolean itf) {
        return new Handle(tag, owner, name, desc);
    }

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
            boolean foundNew = false;
            boolean foundDup = false;
            while (iterator.hasNext()) {
                AbstractInsnNode instruction = iterator.next();
                if (!foundNew && !foundDup && instruction.getOpcode() == NEW &&
                        "org/objectweb/asm/Handle".equals(((TypeInsnNode) instruction).desc)) {
                    foundNew = true;
                    iterator.remove();
                } else if (foundNew && instruction.getOpcode() == DUP) {
                    foundNew = false;
                    foundDup = true;
                    iterator.remove();
                } else if (!foundNew && foundDup && instruction.getOpcode() == INVOKESPECIAL &&
                        "org/objectweb/asm/Handle".equals(((MethodInsnNode) instruction).owner)) {
                    iterator.set(new MethodInsnNode(INVOKESTATIC, "zone/rong/mixinbooter/fix/mixinextras/MixinExtrasFixer", "redirect", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Lorg/objectweb/asm/Handle;"));
                }
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

}
