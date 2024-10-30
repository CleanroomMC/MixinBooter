package zone.rong.mixinbooter.util;

import com.google.common.collect.Maps;
import net.minecraftforge.fml.common.ModMetadata;

import java.util.Map;

public class MockedMetadataCollection {

    public String modListVersion;
    public ModMetadata[] modList;
    public Map<String, ModMetadata> metadatas = Maps.newHashMap();

}
