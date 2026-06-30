package zone.rong.mixinbooter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import zone.rong.mixinbooter.fix.SuppressingClassReader;

import java.io.InputStream;

@Mixin(value = ASMModParser.class, remap = false)
public class ASMModParserMixin {

    @Shadow
    private Type asmType;

    @WrapOperation(method = "<init>", at = @At(value = "NEW", target = "org/objectweb/asm/ClassReader"))
    private ClassReader suppressClassReaderWhenReadingNewClasses(InputStream is, Operation<ClassReader> original) {
        ClassReader reader;
        try {
            reader = original.call(is);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() == null) {
                this.asmType = Type.VOID_TYPE;
                reader = new SuppressingClassReader();
            } else {
                throw e;
            }
        }
        return reader;
    }

}
