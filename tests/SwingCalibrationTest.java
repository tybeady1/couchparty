import com.fairway.monitor.SwingCalibration;

public class SwingCalibrationTest {
  public static void main(String[] args) {
    double[] p = SwingCalibration.pair(40, 10, 44, 12);
    if (Math.abs(p[0] - 1.1) > 1e-9 || p[1] != 2) throw new AssertionError();
    if (SwingCalibration.median(new double[] {1.1, 3, 1.2}, 1) != 1.2) throw new AssertionError();
    if (SwingCalibration.median(new double[] {}, 1) != 1) throw new AssertionError();
    for (double speed : new double[] {0, Double.NaN, 121})
      try {
        SwingCalibration.pair(speed, 10, 44, 12);
        throw new AssertionError();
      } catch (IllegalArgumentException expected) {
      }
    try {
      SwingCalibration.pair(1, 10, 100, 12);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
    }
    try {
      SwingCalibration.pair(40, 10, 44, 60);
      throw new AssertionError();
    } catch (IllegalArgumentException expected) {
    }
    System.out.println(
        "Swing calibration tests passed: reference pairs, robust median, invalid units/readings and"
            + " excessive corrections.");
  }
}
