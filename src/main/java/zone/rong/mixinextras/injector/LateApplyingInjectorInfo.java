package zone.rong.mixinextras.injector;

public interface LateApplyingInjectorInfo {
    void lateInject();

    void latePostInject();

    void wrap(LateApplyingInjectorInfo outer);
}
