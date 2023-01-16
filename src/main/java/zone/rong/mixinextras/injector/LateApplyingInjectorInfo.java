package zone.rong.mixinextras.injector;

public interface LateApplyingInjectorInfo {
  void lateApply();

  void wrap(LateApplyingInjectorInfo outer);
}
