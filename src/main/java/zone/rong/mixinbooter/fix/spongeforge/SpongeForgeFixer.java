package zone.rong.mixinbooter.fix.spongeforge;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import zone.rong.mixinbooter.MixinBooterPlugin;

import java.lang.reflect.Field;
import java.util.Set;

public class SpongeForgeFixer implements IClassTransformer, Opcodes {

    public SpongeForgeFixer() {
        exempt();
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] classBytes) {
        if ("org.spongepowered.asm.util.PrettyPrinter".equals(name)) {
            MixinBooterPlugin.LOGGER.info("Transforming PrettyPrinter to include old removed methods for SpongeForge.");
            return transformPrettyPrinter(classBytes);
        }
        return classBytes;
    }

    private void exempt() {
        try {
            Field launchClassLoader$classLoaderExceptions = LaunchClassLoader.class.getDeclaredField("classLoaderExceptions");
            launchClassLoader$classLoaderExceptions.setAccessible(true);
            ((Set<String>) launchClassLoader$classLoaderExceptions.get(Launch.classLoader)).remove("org.spongepowered.asm.util.");
        } catch (ReflectiveOperationException e) {
            MixinBooterPlugin.LOGGER.fatal("Cannot exempt org.spongepowered.asm.util. package from being excluded by the class loader," +
                    "this will impact SpongeForge from working properly!", e);
        }
    }

    private byte[] transformPrettyPrinter(byte[] classBytes) {
        ClassNode node = new ClassNode();
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(node, 0);

        final String log4jLogger = "org/apache/logging/log4j/Logger";
        final String log4jLevel = "org/apache/logging/log4j/Level";
        final String prettyPrinter = "org/spongepowered/asm/util/PrettyPrinter";
        final String stringType = "Ljava/lang/String;";
        final String log4jLoggerType = "L" + log4jLogger + ";";
        final String log4jLevelType = "L" + log4jLevel + ";";
        final String prettyPrinterType = "L" + prettyPrinter + ";";

//      public PrettyPrinter trace(org.apache.logging.log4j.Level level) {
//          return this.trace(PrettyPrinter.getDefaultLoggerName(), level);
//      }
        MethodVisitor method = node.visitMethod(ACC_PUBLIC, "trace", "(" + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESTATIC, prettyPrinter, "getDefaultLoggerName", "()" + stringType, false);
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "trace", "(" + stringType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();

//      public PrettyPrinter trace(String logger, org.apache.logging.log4j.Level level) {
//          return this.trace(System.err, LogManager.getLogger(logger), level);
//      }
        method = node.visitMethod(ACC_PUBLIC, "trace", "(" + stringType + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKESTATIC, "org/apache/logging/log4j/LogManager", "getLogger", "(" + stringType + ")" + log4jLoggerType, false);
        method.visitVarInsn(ALOAD, 2);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "trace", "(Ljava/io/PrintStream;" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(6, 3);
        method.visitEnd();

//      public PrettyPrinter trace(org.apache.logging.log4j.Logger logger) {
//          return this.trace(System.err, logger);
//      }
        method = node.visitMethod(ACC_PUBLIC, "trace", "(" + log4jLoggerType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "trace", "(Ljava/io/PrintStream;" + log4jLoggerType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();

//      public PrettyPrinter trace(org.apache.logging.log4j.Logger logger, org.apache.logging.log4j.Level level) {
//          return this.trace(System.err, logger, level);
//      }
        method = node.visitMethod(ACC_PUBLIC, "trace", "(" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETSTATIC, "java/lang/System", "err", "Ljava/io/PrintStream;");
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "trace", "(Ljava/io/PrintStream;" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(5, 3);
        method.visitEnd();

//      public PrettyPrinter trace(PrintStream stream, org.apache.logging.log4j.Level level) {
//          return this.trace(stream, PrettyPrinter.getDefaultLoggerName(), level);
//      }
        method = node.visitMethod(ACC_PUBLIC, "trace", "(Ljava/io/PrintStream;" + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKESTATIC, prettyPrinter, "getDefaultLoggerName", "()" + stringType, false);
        method.visitVarInsn(ALOAD, 2);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "trace", "(Ljava/io/PrintStream;" + stringType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(5, 3);
        method.visitEnd();

//      public PrettyPrinter trace(PrintStream stream, String logger, org.apache.logging.log4j.Level level) {
//          return this.trace(stream, LogManager.getLogger(logger), level);
//      }
        method = node.visitMethod(ACC_PUBLIC, "trace", "(Ljava/io/PrintStream;" + stringType + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitMethodInsn(INVOKESTATIC, "org/apache/logging/log4j/LogManager", "getLogger", "(" + stringType + ")" + log4jLoggerType, false);
        method.visitVarInsn(ALOAD, 3);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "trace", "(Ljava/io/PrintStream;" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(6, 4);
        method.visitEnd();

//      public PrettyPrinter trace(PrintStream stream, org.apache.logging.log4j.Logger logger) {
//          return this.trace(stream, logger, org.apache.logging.log4j.Level.DEBUG);
//      }
        method = node.visitMethod(ACC_PUBLIC, "trace", "(Ljava/io/PrintStream;" + log4jLoggerType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitFieldInsn(GETSTATIC, log4jLevel, "DEBUG", log4jLevelType);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "trace", "(Ljava/io/PrintStream;" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(5, 3);
        method.visitEnd();

//      public PrettyPrinter trace(PrintStream stream, org.apache.logging.log4j.Logger logger, org.apache.logging.log4j.Level level) {
//          this.log(logger, level);
//          this.print(stream);
//          return this;
//      }
        method = node.visitMethod(ACC_PUBLIC, "trace", "(Ljava/io/PrintStream;" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 2);
        method.visitVarInsn(ALOAD, 3);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "log", "(" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "print", "(Ljava/io/PrintStream;)" + prettyPrinterType, false);
        method.visitVarInsn(ALOAD, 0);
        method.visitInsn(ARETURN);
        method.visitMaxs(4, 4);
        method.visitEnd();

//      public PrettyPrinter log(org.apache.logging.log4j.Logger logger) {
//          return this.log(logger, org.apache.logging.log4j.Level.INFO);
//      }
        method = node.visitMethod(ACC_PUBLIC, "log", "(" + log4jLoggerType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitFieldInsn(GETSTATIC, log4jLevel, "INFO", log4jLevelType);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "log", "(" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(4, 2);
        method.visitEnd();

//      public PrettyPrinter log(org.apache.logging.log4j.Level level) {
//          return this.log(LogManager.getLogger(PrettyPrinter.getDefaultLoggerName()), level);
//      }
        method = node.visitMethod(ACC_PUBLIC, "log", "(" + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESTATIC, prettyPrinter, "getDefaultLoggerName", "()" + stringType, false);
        method.visitMethodInsn(INVOKESTATIC, "org/apache/logging/log4j/LogManager", "getLogger", "(" + stringType + ")" + log4jLoggerType, false);
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEVIRTUAL, prettyPrinter, "log", "(" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, false);
        method.visitInsn(ARETURN);
        method.visitMaxs(5, 2);
        method.visitEnd();

//      public PrettyPrinter log(org.apache.logging.log4j.Logger logger, org.apache.logging.log4j.Level level) {
//          this.updateWidth();
//          this.logSpecial(logger, level, this.horizontalRule);
//          for (Object line : this.lines) {
//              if (line instanceof PrettyPrinter.ISpecialEntry) {
//                  this.logSpecial(logger, level, (PrettyPrinter.ISpecialEntry) line);
//              } else {
//                  this.logString(logger, level, line.toString());
//              }
//          }
//          this.logSpecial(logger, level, this.horizontalRule);
//          return this;
//      }
        method = node.visitMethod(ACC_PUBLIC, "log", "(" + log4jLoggerType + log4jLevelType + ")" + prettyPrinterType, null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, prettyPrinter, "updateWidth", "()V", false);
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, prettyPrinter, "horizontalRule", "L" + prettyPrinter + "$HorizontalRule;");
        method.visitMethodInsn(INVOKESPECIAL, prettyPrinter, "logSpecial", "(" + log4jLoggerType + log4jLevelType + "L" + prettyPrinter + "$ISpecialEntry;" + ")V", false);
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, prettyPrinter, "lines", "Ljava/util/List;");
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
        method.visitVarInsn(ASTORE, 3);
        Label loopLabel = new Label();
        method.visitLabel(loopLabel);
        method.visitFrame(F_APPEND, 1, new Object[] { "java/util/Iterator" }, 0, null);
        method.visitVarInsn(ALOAD, 3);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        Label endLabel1 = new Label();
        method.visitJumpInsn(IFEQ, endLabel1);
        method.visitVarInsn(ALOAD, 3);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
        method.visitVarInsn(ASTORE, 4);
        method.visitVarInsn(ALOAD, 4);
        method.visitTypeInsn(INSTANCEOF, prettyPrinter + "$ISpecialEntry");
        Label elseLabel = new Label();
        method.visitJumpInsn(IFEQ, elseLabel);
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitVarInsn(ALOAD, 4);
        method.visitTypeInsn(CHECKCAST, prettyPrinter + "$ISpecialEntry");
        method.visitMethodInsn(INVOKESPECIAL, prettyPrinter, "logSpecial", "(" + log4jLoggerType + log4jLevelType + "L" + prettyPrinter + "$ISpecialEntry;" + ")V", false);
        Label frameLabel = new Label();
        method.visitJumpInsn(GOTO, frameLabel);
        method.visitLabel(elseLabel);
        method.visitFrame(F_APPEND, 1, new Object[] { "java/lang/Object" }, 0, null);
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitVarInsn(ALOAD, 4);
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()" + stringType, false);
        method.visitMethodInsn(INVOKESPECIAL, prettyPrinter, "logString", "(" + log4jLoggerType + log4jLevelType + stringType + ")V", false);
        method.visitLabel(frameLabel);
        method.visitFrame(F_CHOP, 1, null, 0, null);
        method.visitJumpInsn(GOTO, loopLabel);
        method.visitLabel(endLabel1);
        method.visitFrame(F_CHOP, 1, null, 0, null);
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, prettyPrinter, "horizontalRule", "L" + prettyPrinter + "$HorizontalRule;");
        method.visitMethodInsn(INVOKESPECIAL, prettyPrinter, "logSpecial", "(" + log4jLoggerType + log4jLevelType + "L" + prettyPrinter + "$ISpecialEntry;" + ")V", false);
        method.visitVarInsn(ALOAD, 0);
        method.visitInsn(ARETURN);
        method.visitMaxs(4, 5);
        method.visitEnd();

//      private void logSpecial(org.apache.logging.log4j.Logger logger, org.apache.logging.log4j.Level level, PrettyPrinter.ISpecialEntry line) {
//          logger.log(level, "/*{}*/", line.toString());
//      }
        method = node.visitMethod(ACC_PRIVATE, "logSpecial", "(" + log4jLoggerType + log4jLevelType + "L" + prettyPrinter + "$ISpecialEntry;" + ")V", null, null);
        method.visitCode();
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitLdcInsn("/*{}*/");
        method.visitInsn(ICONST_1);
        method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        method.visitInsn(DUP);
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ALOAD, 3);
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "toString", "()" + stringType, false);
        method.visitInsn(Opcodes.AASTORE);
        method.visitMethodInsn(INVOKEINTERFACE, log4jLogger, "log", "(" + log4jLevelType + stringType + "[Ljava/lang/Object;)V", true);
        method.visitInsn(RETURN);
        method.visitMaxs(5, 4);
        method.visitEnd();

//      private void logString(org.apache.logging.log4j.Logger logger, org.apache.logging.log4j.Level level, String line) {
//          if (line != null) {
//              logger.log(level, "/* {} */", String.format("%-" + this.width + "s", line));
//          }
//      }
        method = node.visitMethod(ACC_PRIVATE, "logString", "(" + log4jLoggerType + log4jLevelType + stringType + ")V", null, null);
        method.visitCode();
        Label endLabel2 = new Label();
        method.visitVarInsn(ALOAD, 3);
        method.visitJumpInsn(IFNULL, endLabel2);
        method.visitVarInsn(ALOAD, 1);
        method.visitVarInsn(ALOAD, 2);
        method.visitLdcInsn("/* {} */");
        method.visitInsn(ICONST_1);
        method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        method.visitInsn(DUP);
        method.visitInsn(ICONST_0);
        method.visitTypeInsn(NEW, "java/lang/StringBuilder");
        method.visitInsn(DUP);
        method.visitLdcInsn("%-");
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(" + stringType + ")V", false);
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, prettyPrinter, "width", "I");
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
        method.visitLdcInsn("s");
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(" + stringType + ")Ljava/lang/StringBuilder;", false);
        method.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()" + stringType, false);
        method.visitInsn(ICONST_1);
        method.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        method.visitInsn(DUP);
        method.visitInsn(ICONST_0);
        method.visitVarInsn(ALOAD, 3);
        method.visitInsn(Opcodes.AASTORE);
        method.visitMethodInsn(INVOKESTATIC, "java/lang/String", "format", "(" + stringType + "[Ljava/lang/Object;)" + stringType, false);
        method.visitInsn(Opcodes.AASTORE);
        method.visitMethodInsn(INVOKEINTERFACE, log4jLogger, "log", "(" + log4jLevelType + stringType + "[Ljava/lang/Object;)V", true);
        method.visitLabel(endLabel2);
        method.visitFrame(F_SAME, 0, null, 0, null);
        method.visitInsn(RETURN);
        method.visitMaxs(11, 4);
        method.visitEnd();

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

}
