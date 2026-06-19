package zone.rong.mixinbooter.service;

import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.service.IMixinAuditTrail;
import zone.rong.mixinbooter.Tags;
import zone.rong.mixinbooter.util.MixinBooterLogFile;

public class MixinBooterAuditTrail implements IMixinAuditTrail {

    private static final String NAME = Tags.MOD_ID + "-Audit";

    @Override
    public void onApply(String className, String mixinName) {
        MixinBooterLogFile.get().write(Level.INFO, NAME, "APPLY " + mixinName + " -> " + className, null);
    }

    @Override
    public void onPostProcess(String className) {
        MixinBooterLogFile.get().write(Level.INFO, NAME, "POSTPROCESS " + className, null);
    }

    @Override
    public void onGenerate(String className, String generatorName) {
        MixinBooterLogFile.get().write(Level.INFO, NAME, "GENERATE " + className + " (by " + generatorName + ")", null);
    }

}
