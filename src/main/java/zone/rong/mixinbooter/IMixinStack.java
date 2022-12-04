package zone.rong.mixinbooter;

import zone.rong.mixinbooter.internal.stacktrace.MixinStack;

public interface IMixinStack {

    /**
     * @param stacktrace The stack trace you wish to check for mixins
     * @return an object implementing {@link IMixinStack} .
     */
    static IMixinStack createStackReport(StackTraceElement[] stacktrace) {
        return new MixinStack(stacktrace);
    }

    /**
     * @param throwable The exception you wish to check for mixins
     * @return an object implementing {@link IMixinStack} .
     */
    static IMixinStack createStackReport(Throwable throwable) {
        return new MixinStack(throwable.getStackTrace());
    }

    /**
     * Checks if a formatted message is present
     *
     * @return true if its present, false if not.
     */
    boolean isStackMessagePresent();

    /**
     * @return a formatted stack message, or a error message.
     */
    String getStackMessage();

    /**
     * Checks if mixins are present in the supplied stack trace
     *
     * @return true if its present, false if not.
     */
    boolean isMixinStackPresent();

    /**
     * Checks if a particular MixinConfig(MixinJSon) is present in the supplied stack trace
     *
     * @return true if its present, false if not.
     */
    boolean isMixinPartOfStackTrace(String mixinJson);

}
