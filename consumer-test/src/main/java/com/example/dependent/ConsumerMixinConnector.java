package com.example.dependent;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class ConsumerMixinConnector implements IMixinConnector {

    @Override
    public void connect() {
        LogManager.getLogger("Consumer Test|MixinConnector").info("Success.");
        Mixins.addConfiguration("mixin.connector.json");
    }

}
