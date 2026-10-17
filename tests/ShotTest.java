import com.fairway.monitor.Shot;

public class ShotTest {
  public static void main(String[] args) {
    close(Shot.distance(100, true), 91.44);
    close(Shot.speed(100, true), 160.9344);
    close(Shot.distance(155, false), 155);
    Shot shot = new Shot("7 Iron", "manual", 155, 110, 20, 6500, -8, 123);
    if (!shot.csv().equals("123,7 Iron,manual,155.0,110.0,20.0,6500.0,-8.0,Normal ball"))
      throw new AssertionError("CSV units/order");
    Shot foam = new Shot("7 Iron", "manual", 20, 30, 20, 1000, 0, 123, "Foam ball");
    if (!foam.csv().endsWith(",Foam ball")) throw new AssertionError("Ball type missing from CSV");
    for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0, 451}) {
      try {
        new Shot("Driver", "manual", bad, 150, 12, 2500, 0, 0);
        throw new AssertionError("Accepted invalid carry");
      } catch (IllegalArgumentException expected) {
      }
    }
    try {
      new Shot("Driver", "manual", 200, 150, 12, 2500, -151, 0);
      throw new AssertionError("Accepted invalid offline");
    } catch (IllegalArgumentException expected) {
    }
    System.out.println("Shot tests passed: conversions, CSV and invalid measurements.");
  }

  static void close(double actual, double expected) {
    if (Math.abs(actual - expected) > 0.00001) throw new AssertionError(actual + " != " + expected);
  }
}
