import com.fairway.monitor.RolloutCalibration;

public class RolloutCalibrationTest {
  public static void main(String[] args) {
    double c = RolloutCalibration.coefficient(1, 2, .2);
    if (Math.abs(RolloutCalibration.estimate(2, new double[] {c}) - 8) > 1e-8)
      throw new AssertionError("speed squared scaling");
    if (RolloutCalibration.estimate(1, new double[] {2, 100, 2}) != 2)
      throw new AssertionError("median resists outlier");
    if (RolloutCalibration.estimate(1, new double[] {2, 4}) != 3)
      throw new AssertionError("even median");
    if (!Double.isNaN(RolloutCalibration.estimate(1, new double[] {})))
      throw new AssertionError("uncalibrated");
    for (double distance : new double[] {Double.NaN, -1, 0, .1, 51}) {
      try {
        RolloutCalibration.coefficient(1, distance, .2);
        throw new AssertionError("invalid distance accepted");
      } catch (IllegalArgumentException expected) {
      }
    }
    try {
      RolloutCalibration.coefficient(Double.NaN, 2, .2);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
    }
    System.out.println(
        "Rollout calibration tests passed: scaling, median, invalid measurements, no-calibration"
            + " state.");
  }
}
