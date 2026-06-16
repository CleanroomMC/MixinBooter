package zone.rong.mixinbooter.util;

import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.spongepowered.asm.mixin.extensibility.IRemapper;
import org.spongepowered.asm.util.ObfuscationUtil;

public class Srg2NotchRemapper implements IRemapper, ObfuscationUtil.IClassRemapper {

    @Override
    public String mapMethodName(String owner, String name, String desc) {
        if (owner == null || name == null || desc == null) {
            return name;
        }
        String newName = FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(owner, name, desc);
        if (!newName.equals(name)) {
            return newName;
        }
        String obfOwner = this.unmap(owner);
        String obfDesc = this.unmapDesc(desc);
        return FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(obfOwner, name, obfDesc);
    }

    @Override
    public String mapFieldName(String owner, String name, String desc) {
        if (owner == null || name == null || desc == null) {
            return name;
        }
        String newName = FMLDeobfuscatingRemapper.INSTANCE.mapFieldName(owner, name, desc);
        if (!newName.equals(name)) {
            return newName;
        }
        String obfOwner = this.unmap(owner);
        String obfDesc = this.unmapDesc(desc);
        return FMLDeobfuscatingRemapper.INSTANCE.mapFieldName(obfOwner, name, obfDesc);
    }

    @Override
    public String map(String typeName) {
        if (typeName == null) {
            return null;
        }
        return FMLDeobfuscatingRemapper.INSTANCE.map(typeName);
    }

    @Override
    public String unmap(String typeName) {
        if (typeName == null) {
            return null;
        }
        return FMLDeobfuscatingRemapper.INSTANCE.unmap(typeName);
    }

    @Override
    public String mapDesc(String desc) {
        if (desc == null) {
            return null;
        }
        return FMLDeobfuscatingRemapper.INSTANCE.mapDesc(desc);
    }

    @Override
    public String unmapDesc(String desc) {
        if (desc == null) {
            return null;
        }
        String newDesc = ObfuscationUtil.unmapDescriptor(desc, this);
        return newDesc != null ? newDesc : desc;
    }

}
