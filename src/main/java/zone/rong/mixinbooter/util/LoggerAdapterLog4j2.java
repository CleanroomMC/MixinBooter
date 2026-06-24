package zone.rong.mixinbooter.util;

import org.apache.logging.log4j.LogManager;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.logging.LoggerAdapterAbstract;
import org.spongepowered.asm.service.mojang.MixinAuditFile;

public class LoggerAdapterLog4j2 extends LoggerAdapterAbstract {

    private static final org.apache.logging.log4j.Level[] LEVELS = {
        org.apache.logging.log4j.Level.FATAL,
        org.apache.logging.log4j.Level.ERROR,
        org.apache.logging.log4j.Level.WARN,
        org.apache.logging.log4j.Level.INFO,
        org.apache.logging.log4j.Level.DEBUG,
        org.apache.logging.log4j.Level.TRACE
    };

    private final org.apache.logging.log4j.Logger logger;
    private final MixinAuditFile file;

    public LoggerAdapterLog4j2(String name, MixinAuditFile file) {
        super(name);
        this.logger = LogManager.getLogger(name);
        this.file = file;
    }

    @Override
    public String getType() {
        return "Log4j2";
    }

    @Override
    public void catching(Level level, Throwable t) {
        this.logger.catching(LEVELS[level.ordinal()], t);
        this.file.write(level, this.getId(), "Catching " + t, t);
    }

    @Override
    public void catching(Throwable t) {
        this.logger.catching(t);
        this.file.write(Level.WARN, this.getId(), "Catching " + t, t);
    }

    @Override
    public void log(Level level, String message, Object... params) {
        this.logger.log(LEVELS[level.ordinal()], message, params);
        LoggerAdapterAbstract.FormattedMessage formatted = new LoggerAdapterAbstract.FormattedMessage(message, params);
        this.file.write(level, this.getId(), formatted.getMessage(), formatted.getThrowable());
    }

    @Override
    public void log(Level level, String message, Throwable t) {
        this.logger.log(LEVELS[level.ordinal()], message, t);
        this.file.write(level, this.getId(), message, t);
    }

    @Override
    public <T extends Throwable> T throwing(T t) {
        this.file.write(Level.WARN, this.getId(), "Throwing " + t, t);
        return this.logger.throwing(t);
    }

}
