package com.fairway.monitor;

import java.util.*;

/** Empirical stopping-distance model calibrated against the same early camera speed statistic. */
public final class RolloutCalibration {
  private RolloutCalibration() {}

  public static double coefficient(double speed, double distance, double observed) {
    if (!Double.isFinite(speed)
        || speed < .04
        || speed > 8
        || !Double.isFinite(distance)
        || distance < .05
        || distance > 50
        || !Double.isFinite(observed)
        || observed < 0
        || distance < observed)
      throw new IllegalArgumentException(
          "Enter a stopping distance of 0.05–50 m, at least the observed distance. A valid putt"
              + " speed is required.");
    return distance / (speed * speed);
  }

  public static double estimate(double speed, double[] coefficients) {
    if (!Double.isFinite(speed) || speed < .04 || speed > 8 || coefficients.length == 0)
      return Double.NaN;
    double[] copy = coefficients.clone();
    for (double c : copy) if (!Double.isFinite(c) || c <= 0) return Double.NaN;
    Arrays.sort(copy);
    int mid = copy.length / 2;
    double median = copy.length % 2 == 0 ? (copy[mid - 1] + copy[mid]) / 2 : copy[mid];
    return median * speed * speed;
  }
}
