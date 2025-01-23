package zone.rong.mixinbooter;

import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;
import net.minecraftforge.fml.common.versioning.InvalidVersionSpecificationException;
import net.minecraftforge.fml.common.versioning.VersionRange;

import java.util.Collections;
import java.util.Set;

public class MixinBooterModContainer extends DummyModContainer {

    public MixinBooterModContainer() {
        super(new ModMetadata());
        MixinBooterPlugin.LOGGER.info("Initializing MixinBooter's Mod Container.");
        ModMetadata meta = this.getMetadata();
        meta.modId = Tags.MOD_ID;
        meta.name = Tags.MOD_NAME;
        meta.description = "A mod that provides the Sponge Mixin library, a standard API for mods to load mixins targeting Minecraft and other mods, and associated useful utilities on 1.8 - 1.12.2.";
        meta.credits = "Thanks to LegacyModdingMC + Fabric for providing the initial mixin fork.";
        meta.version = Tags.VERSION;
        meta.logoFile = "/icon.png";
        meta.authorList.add("Rongmario");
    }

    @Override
    public boolean registerBus(EventBus bus, LoadController controller) {
        bus.register(this);
        return true;
    }

    @Override
    public Set<ArtifactVersion> getRequirements() {
        try {
            if ("1.12.2".equals(MixinBooterPlugin.getMinecraftVersion())) {
                try {
                    return Collections.singleton(new SpongeForgeArtifactVersion());
                } catch (InvalidVersionSpecificationException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (Throwable ignored) { }
        return Collections.emptySet();
    }

    // Thank you SpongeForge ^_^
    private static class SpongeForgeArtifactVersion extends DefaultArtifactVersion {

        public SpongeForgeArtifactVersion() throws InvalidVersionSpecificationException {
            super("spongeforge", VersionRange.createFromVersionSpec("[7.4.8,)"));
        }

        @Override
        public boolean containsVersion(ArtifactVersion source) {
            if (source == this) {
                return true;
            }
            String version = source.getVersionString();
            String[] hyphenSplits = version.split("-");
            if (hyphenSplits.length > 1) {
                if (hyphenSplits[hyphenSplits.length - 1].startsWith("RC")) {
                    version = hyphenSplits[hyphenSplits.length - 2];
                } else {
                    version = hyphenSplits[hyphenSplits.length - 1];
                }
            }
            source = new DefaultArtifactVersion(source.getLabel(), version);
            return super.containsVersion(source);
        }
    }

}
