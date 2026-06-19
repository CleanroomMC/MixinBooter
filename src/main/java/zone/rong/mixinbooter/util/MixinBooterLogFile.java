package zone.rong.mixinbooter.util;

import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.logging.Level;
import zone.rong.mixinbooter.Tags;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * A self-contained sink that mirrors mixin activity into {@code logs/mixinbooter.log}.
 * <p>
 * Everything emitted through {@link LoggerAdapterLog4j2} (i.e. anything obtained from
 * {@link zone.rong.mixinbooter.service.MixinBooterService#createLogger}
 * is piped here alongside the {@link zone.rong.mixinbooter.service.MixinBooterAuditTrail audit
 * trail} events, giving a single mixin-focused log file separate from the global game log.
 * <p>
 * Plain file writer is chosen rather than attaching a Log4j2 appender.
 * As appenders and other log4j2 require annoying reflections when done at runtime.
 */
public final class MixinBooterLogFile {

    public static final String ENABLED_PROPERTY = Tags.MOD_ID + ".auditTrail";

    private static final String MESSAGE_FORMAT = "[%s] [%s/%s] [%s]: %s%s";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final MixinBooterLogFile INSTANCE = new MixinBooterLogFile();

    public static MixinBooterLogFile get() {
        return INSTANCE;
    }

    private Writer writer;
    private boolean opened;

    private MixinBooterLogFile() { }

    private void open() {
        this.opened = true;
        if ("false".equalsIgnoreCase(System.getProperty(ENABLED_PROPERTY))) {
            return;
        }
        try {
            File dir = new File("logs");
            dir.mkdirs();
            this.writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(dir, Tags.MOD_ID + ".log"), false), StandardCharsets.UTF_8));
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, Tags.MOD_NAME + "/LogCloser"));
        } catch (Throwable t) {
            System.err.println("[" + Tags.MOD_NAME + "]" + " Unable to open logs/mixinbooter.log: " + t);
        }
    }

    /**
     * Append a single entry, flushing immediately so the file survives a hard crash. Never throws.
     *
     * @param level   severity
     * @param name    logger name
     * @param message formatted message
     * @param t       optional throwable to append as a stack trace
     */
    public void write(Level level, String name, String message, Throwable t) {
        synchronized (this) {
            if (!this.opened) {
                this.open();
            }
            if (this.writer == null) {
                return;
            }
            try {
                String threadName = Thread.currentThread().getName();
                this.writer.write(String.format(MESSAGE_FORMAT,
                        LocalTime.now().format(TIME),
                        threadName,
                        level,
                        name,
                        message,
                        System.lineSeparator()
                ));
                if (t != null) {
                    PrintWriter pw = new PrintWriter(this.writer);
                    t.printStackTrace(pw);
                    pw.flush(); // flush only; closing would close the underlying writer
                }
                this.writer.flush();
            } catch (IOException ignored) { }
        }
    }

    private void close() {
        synchronized (this) {
            IOUtils.closeQuietly(this.writer);
        }
    }

}
