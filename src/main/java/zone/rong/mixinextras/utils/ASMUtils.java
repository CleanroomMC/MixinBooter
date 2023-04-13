package zone.rong.mixinextras.utils;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.stream.Collectors;

public class ASMUtils {
    public static String annotationToString(AnnotationNode annotation) {
        StringBuilder builder = new StringBuilder("@").append(typeToString(Type.getType(annotation.desc)));
        List<Object> values = annotation.values;
        if (values == null || values.isEmpty()) {
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
            return '{' + list.stream().map(ASMUtils::valueToString).collect(Collectors.joining(", ")) + '}';
        }
        return value.toString();
    }

    public static boolean isPrimitive(Type type) {
        return type.getDescriptor().length() == 1;
    }

    public static MethodInsnNode getInvokeInstruction(ClassNode owner, MethodNode method) {
        boolean isInterface = (owner.access & Opcodes.ACC_INTERFACE) != 0;
        int opcode = (method.access & Opcodes.ACC_STATIC) != 0 ? Opcodes.INVOKESTATIC
                : (method.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL
                : isInterface ? Opcodes.INVOKEINTERFACE
                : Opcodes.INVOKEVIRTUAL;
        return new MethodInsnNode(
                opcode, owner.name, method.name, method.desc, isInterface
        );
    }

    public static int getDummyOpcodeForType(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return Opcodes.ICONST_0;
            case Type.FLOAT:
                return Opcodes.FCONST_0;
            case Type.LONG:
                return Opcodes.LCONST_0;
            case Type.DOUBLE:
                return Opcodes.DCONST_0;
            case Type.ARRAY:
            case Type.OBJECT:
                return Opcodes.ACONST_NULL;
            default:
                throw new UnsupportedOperationException();
        }
    }
}
