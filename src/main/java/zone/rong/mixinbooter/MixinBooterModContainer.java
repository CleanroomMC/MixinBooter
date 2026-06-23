package zone.rong.mixinbooter;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.MetadataCollection;
import net.minecraftforge.fml.common.ModMetadata;
import org.spongepowered.asm.service.MixinService;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;

public class MixinBooterModContainer extends DummyModContainer {

    private static ModMetadata loadMetadata() {
        try {
            String self = MixinBooterModContainer.class.getResource("MixinBooterModContainer.class").toString();
            URL url = new URL(self.substring(0, self.indexOf("!/") + 2) + "mcmod.info");
            try (InputStream inputStream = url.openStream()) {
                return MetadataCollection.from(inputStream, Tags.MOD_ID).getMetadataForId(Tags.MOD_ID, Collections.emptyMap());
            }
        } catch (Throwable t) {
            MixinService.getService().getLogger(Tags.MOD_NAME).warn("Failed to read mcmod.info metadata, using fallback.", t);
        }
        ModMetadata meta = new ModMetadata();
        meta.modId = Tags.MOD_ID;
        meta.name = Tags.MOD_NAME;
        meta.version = Tags.VERSION;
        meta.authorList.add("Rongmario");
        return meta;
    }

    public MixinBooterModContainer() {
        super(loadMetadata());
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

}
