package zone.rong.mixinbooter.api;

import java.util.List;

public interface IMixinLogGenerator {

    List<String> getMixinConfigs();

    boolean shouldMixinReportCustomMessage(String mixinConfig);

    String onMixinMessage(String mixinConfig);

}
