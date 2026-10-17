import com.fairway.monitor.SpinTracker;
import java.util.Arrays;

public class SpinTrackerTest {
  static double[] angles(double rpm, int n) {
    double[] a = new double[n];
    for (int i = 0; i < n; i++) a[i] = (170 + i * rpm * 6 / 240 + 180) % 360 - 180;
    return a;
  }

  static void reject(double[] a, double max) {
    try {
      SpinTracker.fit(a, 240, max);
      throw new AssertionError("Expected rejection");
    } catch (IllegalArgumentException expected) {
    }
  }

  public static void main(String[] args) {
    if (Math.abs(SpinTracker.fit(angles(600, 12), 240, 1000).rpm - 600) > 1e-8)
      throw new AssertionError("wrap fit");
    if (Math.abs(SpinTracker.fit(angles(-600, 12), 240, 1000).rpm + 600) > 1e-8)
      throw new AssertionError("reverse fit");
    reject(new double[12], 1000);
    reject(angles(600, 4), 1000);
    reject(angles(2000, 12), 1000);
    reject(angles(600, 12), 3000);
    int w = 180;
    int[] p = new int[w * w];
    Arrays.fill(p, 0xff202020);
    for (int y = 0; y < w; y++)
      for (int x = 0; x < w; x++) {
        if (Math.hypot(x - 90, y - 90) <= 30) p[y * w + x] = 0xffdddddd;
        if (Math.hypot(x - 105, y - 90) <= 4) p[y * w + x] = 0xffee2222;
      }
    SpinTracker.Marker m = SpinTracker.locate(p, w, w, 90, 90, 65);
    if (m == null || Math.abs(m.angle) > 1 || Math.abs(m.r - 30) > 1)
      throw new AssertionError("red marker geometry");
    for (int n = 0; n < p.length; n++) if (p[n] == 0xffee2222) p[n] = 0xffdddddd;
    if (SpinTracker.locate(p, w, w, 90, 90, 65) != null)
      throw new AssertionError("unmarked ball accepted");
    System.out.println(
        "Spin tracker tests passed: wrapped/reverse RPM, marker location, missing marker,"
            + " short/static tracks, sampling ceiling.");
  }
}
