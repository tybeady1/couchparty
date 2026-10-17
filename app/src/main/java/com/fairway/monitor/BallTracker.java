package com.fairway.monitor;

import java.util.*;

/** Experimental bright moving object tracker; all coordinates are image-plane pixels. */
public final class BallTracker {
  public static final class Point {
    public final double x, y;
    public final long us;

    public Point(double x, double y, long us) {
      this.x = x;
      this.y = y;
      this.us = us;
    }
  }

  public static final class Result {
    public final List<Point> points;
    public final double metresPerSecond, angle, fit;

    Result(List<Point> p, double speed, double angle, double fit) {
      points = p;
      metresPerSecond = speed;
      this.angle = angle;
      this.fit = fit;
    }
  }

  private int[] previous;
  private final ArrayList<ArrayList<Point>> tracks = new ArrayList<>();
  private int width;

  public void frame(int[] pixels, int w, int h, long us) {
    width = w;
    if (previous == null || previous.length != pixels.length) {
      previous = pixels.clone();
      return;
    }
    boolean[] mask = new boolean[pixels.length];
    for (int i = 0; i < pixels.length; i++) {
      int v = pixels[i], p = previous[i];
      int lum = (((v >> 16) & 255) + ((v >> 8) & 255) + (v & 255)) / 3;
      int old = (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255)) / 3;
      mask[i] = lum > 185 && lum - old > 35;
    }
    previous = pixels.clone();
    ArrayList<Point> candidates = new ArrayList<>();
    int[] queue = new int[pixels.length];
    for (int i = 0; i < mask.length; i++)
      if (mask[i]) {
        int head = 0, tail = 0, minX = w, maxX = 0, minY = h, maxY = 0;
        double sx = 0, sy = 0;
        queue[tail++] = i;
        mask[i] = false;
        while (head < tail) {
          int at = queue[head++], x = at % w, y = at / w;
          sx += x;
          sy += y;
          minX = Math.min(minX, x);
          maxX = Math.max(maxX, x);
          minY = Math.min(minY, y);
          maxY = Math.max(maxY, y);
          for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
              int nx = x + dx, ny = y + dy;
              if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                int j = ny * w + nx;
                if (mask[j]) {
                  mask[j] = false;
                  queue[tail++] = j;
                }
              }
            }
        }
        int bw = maxX - minX + 1, bh = maxY - minY + 1;
        if (tail >= 3
            && tail <= 450
            && bw <= 45
            && bh <= 45
            && bw / (double) bh < 5
            && bh / (double) bw < 5
            && tail / (double) (bw * bh) > .25) candidates.add(new Point(sx / tail, sy / tail, us));
      }
    // Reject frames dominated by scene motion rather than linking arbitrary bright regions.
    if (candidates.size() > 30) return;
    HashSet<ArrayList<Point>> used = new HashSet<>();
    for (Point p : candidates) {
      ArrayList<Point> best = null;
      double cost = Double.MAX_VALUE;
      for (ArrayList<Point> t : tracks) {
        if (used.contains(t)) continue;
        Point last = t.get(t.size() - 1);
        double dt = (p.us - last.us) / 1e6;
        if (dt <= 0 || dt > .10) continue;
        double x = last.x, y = last.y;
        if (t.size() > 1) {
          Point before = t.get(t.size() - 2);
          double ratio = (p.us - last.us) / (double) (last.us - before.us);
          x += (last.x - before.x) * ratio;
          y += (last.y - before.y) * ratio;
        }
        double distance = Math.hypot(p.x - x, p.y - y);
        double limit = t.size() > 1 ? Math.max(10, w * .06) : w * .30;
        if (distance < limit && distance < cost) {
          cost = distance;
          best = t;
        }
      }
      if (best == null) {
        best = new ArrayList<>();
        tracks.add(best);
      }
      best.add(p);
      used.add(best);
    }
    // Bound memory when inspecting noisy footage.
    if (tracks.size() > 2000)
      tracks.removeIf(t -> us - t.get(t.size() - 1).us > 100000 && t.size() < 3);
  }

  public Result result(double frameWidthMetres) {
    if (!Double.isFinite(frameWidthMetres) || frameWidthMetres <= 0)
      throw new IllegalArgumentException("Invalid scale");
    Result best = null;
    double score = 0;
    for (ArrayList<Point> track : tracks) {
      if (track.size() < 3) continue;
      List<Point> p = new ArrayList<>(track.subList(0, Math.min(8, track.size())));
      Point start = p.get(0), end = p.get(p.size() - 1);
      if (Math.hypot(end.x - start.x, end.y - start.y) < 12 || end.us - start.us < 2000) continue;
      double mt = 0, mx = 0, my = 0;
      for (Point q : p) {
        mt += (q.us - start.us) / 1e6;
        mx += q.x;
        my += q.y;
      }
      mt /= p.size();
      mx /= p.size();
      my /= p.size();
      double tt = 0, tx = 0, ty = 0, total = 0, error = 0;
      for (Point q : p) {
        double dt = (q.us - start.us) / 1e6 - mt;
        tt += dt * dt;
        tx += dt * (q.x - mx);
        ty += dt * (q.y - my);
        total += (q.x - mx) * (q.x - mx) + (q.y - my) * (q.y - my);
      }
      if (tt == 0 || total == 0) continue;
      double vx = tx / tt, vy = ty / tt;
      for (Point q : p) {
        double dt = (q.us - start.us) / 1e6 - mt;
        error += Math.pow(q.x - (mx + vx * dt), 2) + Math.pow(q.y - (my + vy * dt), 2);
      }
      double fit = 1 - error / total, speed = Math.hypot(vx, vy) * frameWidthMetres / width;
      if (fit < .90 || speed < 1 || speed > 120) continue;
      double candidate = p.size() * fit;
      if (candidate > score) {
        score = candidate;
        best = new Result(p, speed, Math.toDegrees(Math.atan2(-vy, Math.abs(vx))), fit);
      }
    }
    return best;
  }
}
