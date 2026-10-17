package com.fairway.monitor;

import java.util.Arrays;

/** Paired reference readings correct speed scale and launch-angle bias only. */
public final class SwingCalibration {
  public static double[] pair(
      double rawSpeed, double rawAngle, double referenceSpeed, double referenceAngle) {
    if (!Double.isFinite(rawSpeed)
        || rawSpeed < .5
        || rawSpeed > 120
        || !Double.isFinite(rawAngle)
        || Math.abs(rawAngle) > 90
        || !Double.isFinite(referenceSpeed)
        || referenceSpeed < .5
        || referenceSpeed > 120
        || !Double.isFinite(referenceAngle)
        || referenceAngle < 0
        || referenceAngle > 75)
      throw new IllegalArgumentException(
          "Use valid ball speeds (0.5–120 m/s) and a reference launch angle of 0–75°.");
    double scale = referenceSpeed / rawSpeed, offset = referenceAngle - rawAngle;
    if (scale < .25 || scale > 4 || Math.abs(offset) > 30)
      throw new IllegalArgumentException(
          "Difference is too large. Check units, shot pairing and camera calibration.");
    return new double[] {scale, offset};
  }

  public static double median(double[] values, double fallback) {
    if (values.length == 0) return fallback;
    double[] s = values.clone();
    Arrays.sort(s);
    int n = s.length / 2;
    return s.length % 2 == 0 ? (s[n - 1] + s[n]) / 2 : s[n];
  }
}
