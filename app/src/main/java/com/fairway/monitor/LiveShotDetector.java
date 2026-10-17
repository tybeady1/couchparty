package com.fairway.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stationary-ball lock and one-event-per-impact tracking. Coordinates use the displayed image. */
public final class LiveShotDetector {
  public enum Mode {
    SWING,
    PUTT
  }

  public enum State {
    SEARCHING,
    SETTLING,
    READY,
    TRACKING,
    COOLDOWN
  }

  public static final class Point {
    public final double x, y;
    public final long ns;

    Point(double x, double y, long ns) {
      this.x = x;
      this.y = y;
      this.ns = ns;
    }
  }

  public static final class Reading {
    public final Mode mode;
    public final double speed, angle, observedDistance, fit;
    public final List<Point> path;

    Reading(Mode mode, double speed, double angle, double distance, double fit, List<Point> path) {
      this.mode = mode;
      this.speed = speed;
      this.angle = angle;
      this.observedDistance = distance;
      this.fit = fit;
      this.path = Collections.unmodifiableList(new ArrayList<>(path));
    }
  }

  private static final class Blob {
    final double x, y, r;
    final int area;

    Blob(double x, double y, int area) {
      this.x = x;
      this.y = y;
      this.area = area;
      r = Math.sqrt(area / Math.PI);
    }
  }

  private final Mode mode;
  private final BallType ballType;
  private final double metres, targetX, targetY;
  private State state = State.SEARCHING;
  private Blob anchor, last;
  private long settledAt, lastSeen, cooldownUntil, lastFrame;
  private int stableFrames;
  private byte[] previous;
  private boolean[] mask;
  private int[] queue;
  private final ArrayList<Point> path = new ArrayList<>();
  private String message = "Tap the stationary ball in the live view.";

  public LiveShotDetector(Mode mode, double metres, double targetX, double targetY) {
    this(mode, metres, targetX, targetY, BallType.NORMAL);
  }

  public LiveShotDetector(
      Mode mode, double metres, double targetX, double targetY, BallType ballType) {
    if (!Double.isFinite(metres) || metres < .1 || metres > 50)
      throw new IllegalArgumentException("Invalid calibration");
    this.mode = mode;
    this.metres = metres;
    this.targetX = targetX;
    this.targetY = targetY;
    this.ballType = ballType;
  }

  public State state() {
    return state;
  }

  public String message() {
    return message;
  }

  public List<Point> path() {
    return new ArrayList<>(path);
  }

  public Reading frame(byte[] luma, int w, int h, long ns) {
    if (luma.length != w * h) throw new IllegalArgumentException("Frame dimensions");
    if (ns <= lastFrame) return null;
    if (lastFrame != 0 && ns - lastFrame > 300_000_000L) reset("Frame gap — hold still to re-arm.");
    lastFrame = ns;
    if (mask == null || mask.length != luma.length) {
      mask = new boolean[luma.length];
      queue = new int[luma.length];
      previous = null;
    }
    boolean moved = sceneMoved(luma, w, h);
    previous = luma.clone();
    if (targetX < 0) {
      message = "Tap the stationary ball in the live view.";
      return null;
    }
    if (moved) {
      reset("Camera or lighting changed — hold still to re-arm.");
      return null;
    }
    if (state == State.COOLDOWN) {
      if (ns < cooldownUntil) return null;
      reset("Replace the ball at the target to re-arm.");
    }
    List<Blob> blobs = blobs(luma, w, h);
    double tx = targetX * w, ty = targetY * h;
    if (state == State.SEARCHING || state == State.SETTLING) {
      Blob b = nearest(blobs, tx, ty, Math.max(24, w * .055), null);
      if (b == null) {
        reset("Place a white ball at the target on a darker surface.");
        return null;
      }
      if (anchor == null || Math.hypot(b.x - anchor.x, b.y - anchor.y) > Math.max(2, b.r * .3)) {
        anchor = b;
        settledAt = ns;
        stableFrames = 1;
        state = State.SETTLING;
        message = "Ball found — hold still…";
      } else {
        stableFrames++;
        if (ns - settledAt >= 600_000_000L && stableFrames >= 6) {
          state = State.READY;
          message =
              mode == Mode.PUTT
                  ? "READY — putt when you’re ready"
                  : "READY — swing when you’re ready";
        }
      }
      last = b;
      lastSeen = ns;
      return null;
    }
    double maxJump = mode == Mode.PUTT ? w * .16 : w * .48;
    double px = last.x, py = last.y;
    if (state == State.TRACKING && path.size() >= 2) {
      Point a = path.get(path.size() - 2), b = path.get(path.size() - 1);
      double ratio = (ns - b.ns) / (double) (b.ns - a.ns);
      px += (b.x - a.x) * ratio;
      py += (b.y - a.y) * ratio;
      maxJump = Math.max(12, last.r * 3);
    }
    Blob b = nearest(blobs, px, py, maxJump, anchor);
    if (b == null) {
      if (ns - lastSeen > 180_000_000L) {
        if (state == State.TRACKING) return finish(w, ns);
        reset("Ball lost before a track formed — replace it to re-arm.");
      }
      return null;
    }
    if (state == State.READY) {
      if (Math.hypot(b.x - anchor.x, b.y - anchor.y) < Math.max(3, anchor.r * .65)) {
        last = b;
        lastSeen = ns;
        return null;
      }
      path.clear();
      path.add(new Point(last.x, last.y, lastSeen));
      state = State.TRACKING;
      message = "Tracking ball…";
    }
    path.add(new Point(b.x, b.y, ns));
    last = b;
    lastSeen = ns;
    Point first = path.get(0);
    if (path.size() >= 8 && Math.hypot(b.x - first.x, b.y - first.y) >= Math.max(10, anchor.r * 2))
      return finish(w, ns);
    if (ns - first.ns > (mode == Mode.PUTT ? 1_500_000_000L : 700_000_000L)) return finish(w, ns);
    return null;
  }

  private Reading finish(int width, long ns) {
    Reading r = fit(width);
    state = State.COOLDOWN;
    cooldownUntil = ns + 1_500_000_000L;
    message =
        r == null
            ? "Track too short or inconsistent — replace ball to retry."
            : "Captured — replace ball at the target for the next shot.";
    return r;
  }

  private Reading fit(int width) {
    // Drop the pre-impact anchor: its timestamp may precede contact by almost a frame.
    if (path.size() < 5) return null;
    List<Point> moving = path.subList(1, Math.min(8, path.size()));
    Point first = moving.get(0), end = moving.get(moving.size() - 1);
    double displacement = Math.hypot(end.x - first.x, end.y - first.y);
    if (displacement < 5) return null;
    double mt = 0, mx = 0, my = 0;
    for (Point p : moving) {
      mt += (p.ns - first.ns) / 1e9;
      mx += p.x;
      my += p.y;
    }
    int count = moving.size();
    mt /= count;
    mx /= count;
    my /= count;
    double tt = 0, tx = 0, ty = 0, total = 0, error = 0;
    for (Point p : moving) {
      double dt = (p.ns - first.ns) / 1e9 - mt;
      tt += dt * dt;
      tx += dt * (p.x - mx);
      ty += dt * (p.y - my);
      total += Math.pow(p.x - mx, 2) + Math.pow(p.y - my, 2);
    }
    if (tt <= 0 || total <= 0) return null;
    double vx = tx / tt, vy = ty / tt;
    for (Point p : moving) {
      double dt = (p.ns - first.ns) / 1e9 - mt;
      error += Math.pow(p.x - mx - vx * dt, 2) + Math.pow(p.y - my - vy * dt, 2);
    }
    double quality = 1 - error / total, speed = Math.hypot(vx, vy) * metres / width;
    if (quality < .9
        || speed < (mode == Mode.PUTT ? .04 : .5)
        || speed > (mode == Mode.PUTT ? 8 : 120)) return null;
    return new Reading(
        mode,
        speed,
        Math.toDegrees(Math.atan2(-vy, Math.abs(vx))),
        displacement * metres / width,
        quality,
        path);
  }

  private void reset(String reason) {
    state = State.SEARCHING;
    anchor = null;
    last = null;
    stableFrames = 0;
    path.clear();
    message = reason;
  }

  private boolean sceneMoved(byte[] frame, int w, int h) {
    if (previous == null) return false;
    int changed = 0, n = 0;
    for (int y = 4; y < h; y += 8)
      for (int x = 4; x < w; x += 8) {
        if (last != null && Math.hypot(x - last.x, y - last.y) < last.r * 4) continue;
        int i = y * w + x;
        n++;
        if (Math.abs((frame[i] & 255) - (previous[i] & 255)) > 28) changed++;
      }
    return n > 0 && changed > n * .22;
  }

  private List<Blob> blobs(byte[] frame, int w, int h) {
    // Adaptive brightness threshold around the selected white ball also works in indoor light.
    int peak = 0;
    int cx = (int) (targetX * w), cy = (int) (targetY * h);
    if (anchor == null) {
      for (int y = Math.max(0, cy - 20); y < Math.min(h, cy + 20); y++)
        for (int x = Math.max(0, cx - 20); x < Math.min(w, cx + 20); x++)
          peak = Math.max(peak, frame[y * w + x] & 255);
    } else peak = threshold + 30;
    threshold = Math.max(95, Math.min(220, peak - 30));
    for (int i = 0; i < frame.length; i++) mask[i] = (frame[i] & 255) >= threshold;
    ArrayList<Blob> list = new ArrayList<>();
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
              int xx = x + dx, yy = y + dy;
              if (xx >= 0 && xx < w && yy >= 0 && yy < h) {
                int j = yy * w + xx;
                if (mask[j]) {
                  mask[j] = false;
                  queue[tail++] = j;
                }
              }
            }
        }
        int bw = maxX - minX + 1, bh = maxY - minY + 1;
        if (tail >= 8
            && tail <= 2000
            && bw < 64
            && bh < 64
            && bw / (double) bh < 2.4
            && bh / (double) bw < 2.4
            && tail / (double) (bw * bh) > ballType.minimumFill)
          list.add(new Blob(sx / tail, sy / tail, tail));
      }
    return list;
  }

  private int threshold = 180;

  private Blob nearest(List<Blob> list, double x, double y, double range, Blob reference) {
    Blob best = null;
    double distance = range;
    for (Blob b : list) {
      if (reference != null && (b.area < reference.area * .35 || b.area > reference.area * 2.8))
        continue;
      double d = Math.hypot(b.x - x, b.y - y);
      if (d < distance) {
        distance = d;
        best = b;
      }
    }
    return best;
  }
}
