import com.fairway.monitor.BallTracker;
import java.util.Arrays;

public class BallTrackerTest {
  static final int W = 320, H = 180;

  static int[] frame(int x, int y) {
    int[] a = new int[W * H];
    Arrays.fill(a, 0xff101010);
    for (int dy = -3; dy <= 3; dy++)
      for (int dx = -3; dx <= 3; dx++)
        if (dx * dx + dy * dy <= 9) a[(y + dy) * W + x + dx] = 0xfffafafa;
    return a;
  }

  public static void main(String[] args) {
    BallTracker moving = new BallTracker();
    for (int i = 0; i < 9; i++)
      moving.frame(frame(30 + 20 * i, 120 - 5 * i), W, H, Math.round(i * 1e6 / 120));
    BallTracker.Result r = moving.result(2);
    if (r == null) throw new AssertionError("Known moving ball not detected");
    close(r.metresPerSecond, Math.hypot(20, 5) * 120 * 2 / W, .02);
    close(r.angle, Math.toDegrees(Math.atan2(5, 20)), .01);
    close(moving.result(4).metresPerSecond, r.metresPerSecond * 2, .01);
    BallTracker still = new BallTracker();
    for (int i = 0; i < 10; i++) still.frame(frame(50, 80), W, H, i * 10000);
    if (still.result(2) != null) throw new AssertionError("Stationary ball produced speed");
    BallTracker sparse = new BallTracker();
    for (int i = 0; i < 3; i++) sparse.frame(frame(30 + i * 20, 80), W, H, i * 10000);
    if (sparse.result(2) != null) throw new AssertionError("Insufficient observations accepted");
    BallTracker gap = new BallTracker();
    for (int i = 0; i < 6; i++) gap.frame(frame(30 + i * 20, 80), W, H, i * 200000);
    if (gap.result(2) != null) throw new AssertionError("Long frame gaps accepted");
    System.out.println(
        "Ball tracker tests passed: speed, angle, scale, static scene, insufficient observations"
            + " and time gaps.");
  }

  static void close(double a, double b, double epsilon) {
    if (Math.abs(a - b) > epsilon) throw new AssertionError(a + " != " + b);
  }
}
