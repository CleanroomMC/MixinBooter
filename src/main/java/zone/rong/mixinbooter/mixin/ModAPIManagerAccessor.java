package zone.rong.mixinbooter.mixin;

import net.minecraftforge.fml.common.ModAPIManager;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ModAPIManager.class, remap = false)
public interface ModAPIManagerAccessor {

    @Accessor(value = "dataTable")
    ASMDataTable mixinBooter$getDataTable();

}
