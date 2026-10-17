package com.fairway.monitor;

import java.util.*;

/** Experimental projected marker rotation. Not a 3D spin-axis solver. */
public final class SpinTracker {
  public static final class Result {
    public final double rpm, span;
    public final int points;

    Result(double r, double s, int n) {
      rpm = r;
      span = s;
      points = n;
    }
  }

  public static Result fit(double[] angles, double fps, double maxRpm) {
    if (angles.length < 6
        || !Double.isFinite(fps)
        || fps < 120
        || !Double.isFinite(maxRpm)
        || maxRpm <= 0
        || maxRpm > fps * 10)
      throw new IllegalArgumentException(
          "Need 6+ clear frames and an expected spin ceiling no higher than 10 × capture fps.");
    double[] unwrapped = new double[angles.length];
    unwrapped[0] = angles[0];
    double maxStep = maxRpm * 6 / fps;
    for (int n = 1; n < angles.length; n++) {
      double d = angles[n] - angles[n - 1];
      while (d > 180) d -= 360;
      while (d < -180) d += 360;
      if (!Double.isFinite(d) || Math.abs(d) > maxStep + 2)
        throw new IllegalArgumentException("Marker jump exceeds the selected spin ceiling.");
      unwrapped[n] = unwrapped[n - 1] + d;
    }
    double mean = 0;
    for (double a : unwrapped) {
      if (!Double.isFinite(a)) throw new IllegalArgumentException("Invalid marker");
      mean += a;
    }
    mean /= angles.length;
    double middle = (angles.length - 1) / 2.0, xx = 0, xy = 0, total = 0;
    for (int n = 0; n < angles.length; n++) {
      double x = n - middle;
      xx += x * x;
      xy += x * (unwrapped[n] - mean);
      total += Math.pow(unwrapped[n] - mean, 2);
    }
    double slope = xy / xx, error = 0;
    for (int n = 0; n < angles.length; n++)
      error += Math.pow(unwrapped[n] - mean - slope * (n - middle), 2);
    double span = Math.abs(slope * (angles.length - 1));
    if (span < 60 || total <= 0 || 1 - error / total < .97)
      throw new IllegalArgumentException(
          "Not enough consistent rotation (need at least 60° and a stable track).");
    double rpm = slope * fps / 6;
    if (Math.abs(rpm) > maxRpm)
      throw new IllegalArgumentException("Spin exceeds selected ceiling.");
    return new Result(rpm, span, angles.length);
  }

  public static final class Marker {
    public final double x, y, r, angle;

    Marker(double x, double y, double r, double a) {
      this.x = x;
      this.y = y;
      this.r = r;
      angle = a;
    }
  }

  /** Segment one white ball and its single red dot in a bounded search area. */
  public static Marker locate(int[] pixels, int w, int h, double tx, double ty, double search) {
    int x0 = Math.max(0, (int) (tx - search)),
        x1 = Math.min(w, (int) (tx + search)),
        y0 = Math.max(0, (int) (ty - search)),
        y1 = Math.min(h, (int) (ty + search));
    int[] queue = new int[Math.max(1, (x1 - x0) * (y1 - y0))];
    boolean[] seen = new boolean[queue.length];
    Marker best = null;
    double nearest = search;
    for (int y = y0; y < y1; y++)
      for (int x = x0; x < x1; x++) {
        int idx = (y - y0) * (x1 - x0) + x - x0;
        if (seen[idx] || !ball(pixels[y * w + x])) continue;
        int head = 0, tail = 0;
        queue[tail++] = y * w + x;
        seen[idx] = true;
        double sx = 0, sy = 0, rx = 0, ry = 0;
        int red = 0, minX = w, maxX = 0, minY = h, maxY = 0;
        while (head < tail) {
          int at = queue[head++], px = at % w, py = at / w;
          sx += px;
          sy += py;
          minX = Math.min(minX, px);
          maxX = Math.max(maxX, px);
          minY = Math.min(minY, py);
          maxY = Math.max(maxY, py);
          if (red(pixels[at])) {
            rx += px;
            ry += py;
            red++;
          }
          for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
              int nx = px + dx, ny = py + dy;
              if (nx < x0 || nx >= x1 || ny < y0 || ny >= y1) continue;
              int ni = (ny - y0) * (x1 - x0) + nx - x0;
              if (!seen[ni] && ball(pixels[ny * w + nx])) {
                seen[ni] = true;
                queue[tail++] = ny * w + nx;
              }
            }
        }
        int bw = maxX - minX + 1, bh = maxY - minY + 1;
        double r = (bw + bh) / 4.0, cx = (minX + maxX) / 2.0, cy = (minY + maxY) / 2.0;
        if (bw < 24
            || bh < 24
            || bw > 180
            || bh > 180
            || Math.abs(bw - bh) > Math.max(bw, bh) * .18
            || tail / (double) (bw * bh) < .60
            || red < 4
            || red > tail * .12
            || minX <= x0
            || maxX >= x1 - 1
            || minY <= y0
            || maxY >= y1 - 1) continue;
        double mx = rx / red - cx,
            my = ry / red - cy,
            rad = Math.hypot(mx, my) / r,
            dist = Math.hypot(cx - tx, cy - ty);
        if (rad < .25 || rad > .8 || dist >= nearest) continue;
        // Multiple red dots are rejected by their spread around the centroid.
        double variance = 0;
        for (int n = 0; n < tail; n++)
          if (red(pixels[queue[n]])) {
            double qx = queue[n] % w - rx / red, qy = queue[n] / w - ry / red;
            variance += qx * qx + qy * qy;
          }
        if (variance / red > r * r * .04) continue;
        best = new Marker(cx, cy, r, Math.toDegrees(Math.atan2(-my, mx)));
        nearest = dist;
      }
    return best;
  }

  private static boolean red(int c) {
    int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
    return r > 85 && r > g * 1.5 && r > b * 1.5;
  }

  private static boolean ball(int c) {
    int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
    int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
    return (min > 100 && max - min < 65) || red(c);
  }
}
