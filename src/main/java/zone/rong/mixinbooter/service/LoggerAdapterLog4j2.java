package zone.rong.mixinbooter.service;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;

class LoggerAdapterLog4j2 extends LoggerAdapterAbstract {

    private static final org.apache.logging.log4j.Level[] LEVELS = {
        org.apache.logging.log4j.Level.FATAL,
        org.apache.logging.log4j.Level.ERROR,
        org.apache.logging.log4j.Level.WARN,
        org.apache.logging.log4j.Level.INFO,
        org.apache.logging.log4j.Level.DEBUG,
        org.apache.logging.log4j.Level.TRACE
    };

    private final org.apache.logging.log4j.Logger logger;

    LoggerAdapterLog4j2(String name) {
        super(name);
        this.logger = LogManager.getLogger(name);
    }

    @Override
    public String getType() {
        return "Log4j2";
    }

    @Override
    public void catching(Level level, Throwable t) {
        this.logger.catching(LEVELS[level.ordinal()], t);
    }

    @Override
    public void catching(Throwable t) {
        this.logger.catching(t);
    }

    @Override
    public void log(Level level, String message, Object... params) {
        this.logger.log(LEVELS[level.ordinal()], message, params);
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        this.logger.log(LEVELS[level.ordinal()], message, t);
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        return this.logger.throwing(t);
    }

}
