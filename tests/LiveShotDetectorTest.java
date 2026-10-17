import com.fairway.monitor.BallType;
import com.fairway.monitor.LiveShotDetector;
import java.util.Arrays;

public class LiveShotDetectorTest {
  static final int W = 320, H = 180;

  static byte[] frame(int x, int y) {
    byte[] p = new byte[W * H];
    Arrays.fill(p, (byte) 35);
    if (x >= 6 && x < W - 6)
      for (int dy = -5; dy <= 5; dy++)
        for (int dx = -5; dx <= 5; dx++)
          if (dx * dx + dy * dy <= 25) p[(y + dy) * W + x + dx] = (byte) 220;
    return p;
  }

  static long ns(int i) {
    return i * 33_333_333L;
  }

  static LiveShotDetector armed(LiveShotDetector.Mode mode) {
    LiveShotDetector d = new LiveShotDetector(mode, 2, 60.0 / W, 90.0 / H);
    for (int i = 1; i <= 25; i++)
      if (d.frame(frame(60, 90), W, H, ns(i)) != null)
        throw new AssertionError("Stationary ball triggered");
    if (d.state() != LiveShotDetector.State.READY)
      throw new AssertionError("Did not arm: " + d.message());
    return d;
  }

  public static void main(String[] args) {
    LiveShotDetector d = armed(LiveShotDetector.Mode.PUTT);
    LiveShotDetector.Reading result = null;
    int hits = 0;
    for (int i = 26; i <= 70; i++) {
      LiveShotDetector.Reading r = d.frame(frame(60 + 2 * (i - 25), 90), W, H, ns(i));
      if (r != null) {
        result = r;
        hits++;
      }
    }
    if (hits != 1 || result == null) throw new AssertionError("Expected one putt, got " + hits);
    close(result.speed, .375, .01);
    close(result.angle, 0, .01);
    for (int i = 71; i <= 110; i++)
      if (d.frame(frame(60, 90), W, H, ns(i)) != null)
        throw new AssertionError("Replacement triggered");
    if (d.state() != LiveShotDetector.State.READY) throw new AssertionError("Did not re-arm");
    hits = 0;
    for (int i = 111; i <= 130; i++)
      if (d.frame(frame(60 + 3 * (i - 110), 90), W, H, ns(i)) != null) hits++;
    if (hits != 1) throw new AssertionError("Second putt not captured");
    LiveShotDetector swing = armed(LiveShotDetector.Mode.SWING);
    LiveShotDetector.Reading shot = null;
    for (int i = 26; i <= 36; i++) {
      LiveShotDetector.Reading r =
          swing.frame(frame(60 + 12 * (i - 25), 90 - 3 * (i - 25)), W, H, ns(i));
      if (r != null) shot = r;
    }
    if (shot == null) throw new AssertionError("Swing not captured");
    close(shot.speed, Math.hypot(12, 3) * 30 * 2 / W, .01);
    close(shot.angle, Math.toDegrees(Math.atan2(3, 12)), .01);
    LiveShotDetector lost = armed(LiveShotDetector.Mode.PUTT);
    for (int i = 26; i <= 50; i++)
      if (lost.frame(frame(-1, 90), W, H, ns(i)) != null)
        throw new AssertionError("Disappearance fabricated a shot");
    LiveShotDetector jitter = armed(LiveShotDetector.Mode.PUTT);
    for (int i = 26; i <= 100; i++)
      if (jitter.frame(frame(60 + (i % 3) - 1, 90), W, H, ns(i)) != null)
        throw new AssertionError("Jitter triggered");
    LiveShotDetector gap = armed(LiveShotDetector.Mode.PUTT);
    gap.frame(frame(80, 90), W, H, ns(80));
    if (gap.state() == LiveShotDetector.State.TRACKING)
      throw new AssertionError("Tracked across long gap");
    LiveShotDetector camera = armed(LiveShotDetector.Mode.PUTT);
    byte[] bright = new byte[W * H];
    Arrays.fill(bright, (byte) 130);
    if (camera.frame(bright, W, H, ns(26)) != null
        || camera.state() == LiveShotDetector.State.READY)
      throw new AssertionError("Lighting change retained lock");
    for (BallType type : BallType.values()) {
      LiveShotDetector profile =
          new LiveShotDetector(LiveShotDetector.Mode.PUTT, 2, 60.0 / W, 90.0 / H, type);
      for (int i = 1; i <= 25; i++) profile.frame(frame(60, 90), W, H, ns(i));
      if (profile.state() != LiveShotDetector.State.READY)
        throw new AssertionError(type.label + " cannot lock");
      LiveShotDetector.Reading typed = null;
      for (int i = 26; i <= 45; i++) {
        LiveShotDetector.Reading r = profile.frame(frame(60 + 2 * (i - 25), 90), W, H, ns(i));
        if (r != null) typed = r;
      }
      if (typed == null) throw new AssertionError(type.label + " did not capture");
      close(typed.speed, .375, .01);
    }
    LiveShotDetector wiffle =
        new LiveShotDetector(LiveShotDetector.Mode.PUTT, 2, 60.0 / W, 90.0 / H, BallType.WIFFLE);
    byte[] ring = frame(-1, 90);
    for (int y = -7; y <= 7; y++)
      for (int x = -7; x <= 7; x++)
        if (x * x + y * y <= 49 && x * x + y * y > 25) ring[(90 + y) * W + 60 + x] = (byte) 220;
    for (int i = 1; i <= 25; i++) wiffle.frame(ring, W, H, ns(i));
    if (wiffle.state() != LiveShotDetector.State.READY)
      throw new AssertionError("Perforated ball failed to lock");
    System.out.println(
        "Live detection passed: lock, putt speed, swing angle, one event per shot,"
            + " replacement/re-arm, jitter, lost ball, dropped frames, lighting changes, all ball"
            + " profiles.");
  }

  static void close(double a, double b, double e) {
    if (Math.abs(a - b) > e) throw new AssertionError(a + " != " + b);
  }
}
