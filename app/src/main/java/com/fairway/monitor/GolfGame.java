package com.fairway.monitor;

import java.io.Serializable;
import java.util.*;

/** Deterministic arcade simulation. Virtual distances are not measured real-world carry. */
public final class GolfGame implements Serializable {
  private static final long serialVersionUID = 1L;

  public enum Mode {
    PRACTICE,
    TARGET_RUSH,
    COLOR_CHASE,
    DISTANCE_LADDER,
    MEADOW,
    CASTLE
  }

  public static final double BALL = .23;

  public static final class Target implements Serializable {
    public final double x, z;
    public final int color;
    public final String name;

    Target(double x, double z, int color, String name) {
      this.x = x;
      this.z = z;
      this.color = color;
      this.name = name;
    }
  }

  public static final Target[] TARGETS = {
    new Target(-22, 65, 0xfff5c75e, "Gold"),
    new Target(25, 100, 0xfff18472, "Coral"),
    new Target(-24, 140, 0xff65cfbc, "Mint"),
    new Target(24, 180, 0xff77aef5, "Blue"),
    new Target(0, 225, 0xffc69cf4, "Violet")
  };

  public static final class Obstacle implements Serializable {
    public final double x, z, w, d;
    public final boolean moving;

    Obstacle(double x, double z, double w, double d, boolean moving) {
      this.x = x;
      this.z = z;
      this.w = w;
      this.d = d;
      this.moving = moving;
    }

    public double center(double time) {
      return x + (moving ? Math.sin(time * 1.4) * 3 : 0);
    }
  }

  public static final class Hole implements Serializable {
    public final String name;
    public final int par;
    public final double cupX, cupZ;
    public final List<Obstacle> obstacles = new ArrayList<>(), water = new ArrayList<>();

    Hole(String name, int par, double x, double z) {
      this.name = name;
      this.par = par;
      cupX = x;
      cupZ = z;
    }

    Hole wall(double x, double z, double w, double d) {
      obstacles.add(new Obstacle(x, z, w, d, false));
      return this;
    }

    Hole gate(double z) {
      obstacles.add(new Obstacle(0, z, 3, .7, true));
      return this;
    }

    Hole water(double x, double z, double w, double d) {
      water.add(new Obstacle(x, z, w, d, false));
      return this;
    }
  }

  public final Mode mode;
  public final Hole[] holes;
  public final int[] card = new int[3];
  public double x, y, z, vx, vy, vz, time, lastDistance, aim, launch = 22, power = 55;
  public int shots, points, holeIndex, holeStrokes, revision;
  public boolean moving, holeComplete, roundComplete;
  public String message = "Aim, choose power, and take your shot.", input = "Touch controls";
  public final ArrayList<double[]> trail = new ArrayList<>();
  private double beforeX, beforeZ, shotTime, trailTime;
  private int shotTarget;

  public GolfGame(Mode mode) {
    this.mode = mode;
    holes = course(mode);
    x = 0;
    z = isMini() ? 2 : 0;
    y = isMini() ? BALL : .4;
    power = isMini() ? 4.8 : 55;
    launch = isMini() ? 0 : 22;
  }

  public boolean isMini() {
    return mode == Mode.MEADOW || mode == Mode.CASTLE;
  }

  public String title() {
    switch (mode) {
      case PRACTICE:
        return "Free Practice";
      case TARGET_RUSH:
        return "Target Rush";
      case COLOR_CHASE:
        return "Color Chase";
      case DISTANCE_LADDER:
        return "Distance Ladder";
      case MEADOW:
        return "Meadow Links";
      default:
        return "Castle Cove";
    }
  }

  public Hole hole() {
    return holes[holeIndex];
  }

  public int limit() {
    return mode == Mode.PRACTICE ? 0 : mode == Mode.DISTANCE_LADDER ? 6 : 10;
  }

  public int activeTarget() {
    return moving ? shotTarget : shots % TARGETS.length;
  }

  public int ladderDistance() {
    return 60 + 30 * Math.min(moving ? shots - 1 : shots, 5);
  }

  public String objective() {
    if (isMini())
      return "Hole " + (holeIndex + 1) + " / 3 · " + hole().name + " · Par " + hole().par;
    if (mode == Mode.COLOR_CHASE)
      return "Hit " + TARGETS[activeTarget()].name + " · only the called target scores";
    if (mode == Mode.DISTANCE_LADDER) return "Land within 12 m of " + ladderDistance() + " m";
    return mode == Mode.PRACTICE
        ? "Unlimited shots · touch or live camera input"
        : "10 balls · any target · center 50 / middle 25 / outer 10";
  }

  public synchronized boolean shoot(double speed, double angle, double heading, String source) {
    if (moving
        || holeComplete
        || roundComplete
        || !Double.isFinite(speed)
        || !Double.isFinite(angle)
        || !Double.isFinite(heading)
        || speed <= 0
        || speed > (isMini() ? 12 : 120)
        || (!isMini() && (angle < 1 || angle > 75))) return false;
    if (!isMini()) {
      x = 0;
      z = 0;
      y = .4;
    }
    beforeX = x;
    beforeZ = z;
    shotTarget = shots % TARGETS.length;
    shots++;
    if (isMini()) holeStrokes++;
    double yaw = Math.toRadians(heading), pitch = Math.toRadians(isMini() ? 0 : angle);
    vx = Math.sin(yaw) * speed * Math.cos(pitch);
    vz = Math.cos(yaw) * speed * Math.cos(pitch);
    vy = isMini() ? 0 : speed * Math.sin(pitch);
    moving = true;
    shotTime = 0;
    trailTime = 0;
    trail.clear();
    input = source;
    message = isMini() ? "Ball rolling…" : "Ball in flight…";
    revision++;
    return true;
  }

  public synchronized void advance(double seconds) {
    double remaining = Math.max(0, Math.min(.1, seconds));
    while (remaining > 0) {
      double dt = Math.min(1.0 / 120, remaining);
      step(dt);
      remaining -= dt;
    }
  }

  private void step(double dt) {
    time += dt;
    if (!moving) return;
    shotTime += dt;
    trailTime += dt;
    if (isMini()) putt(dt);
    else flight(dt);
    if (trailTime > .08) {
      trailTime = 0;
      trail.add(new double[] {x, y, z});
      if (trail.size() > 140) trail.remove(0);
    }
    if (moving && shotTime > 18) {
      moving = false;
      message = "Shot settled.";
      if (!isMini()) scoreLanding();
      else checkStrokeLimit();
    }
  }

  private void flight(double dt) {
    double speed = Math.sqrt(vx * vx + vy * vy + vz * vz), drag = .0065 * speed;
    vx -= vx * drag * dt;
    vz -= vz * drag * dt;
    vy -= 9.81 * dt + vy * drag * dt;
    x += vx * dt;
    y += vy * dt;
    z += vz * dt;
    if (y <= .15) {
      y = .15;
      moving = false;
      lastDistance = Math.hypot(x, z);
      scoreLanding();
    }
  }

  private void scoreLanding() {
    int added = 0, hit = -1;
    for (int i = 0; i < TARGETS.length; i++) {
      Target t = TARGETS[i];
      int s = ringScore(Math.hypot(x - t.x, z - t.z));
      if (s > added) {
        added = s;
        hit = i;
      }
    }
    if (mode == Mode.COLOR_CHASE && hit != shotTarget) added = 0;
    if (mode == Mode.DISTANCE_LADDER) {
      double error = Math.abs(z - (60 + 30 * (shots - 1)));
      added = Math.abs(x) <= 45 && error <= 12 ? (int) Math.round(100 * (1 - error / 15)) : 0;
    }
    points += added;
    message = String.format(Locale.US, "%.0f m virtual carry · +%d points", lastDistance, added);
    if (limit() > 0 && shots >= limit()) {
      roundComplete = true;
      message = "Round complete · " + points + " points";
    }
    revision++;
  }

  public static int ringScore(double distance) {
    return distance <= 5 ? 50 : distance <= 11 ? 25 : distance <= 18 ? 10 : 0;
  }

  private void putt(double dt) {
    x += vx * dt;
    z += vz * dt;
    y = BALL;
    if (x < -6 + BALL) {
      x = -6 + BALL;
      vx = Math.abs(vx) * .72;
    }
    if (x > 6 - BALL) {
      x = 6 - BALL;
      vx = -Math.abs(vx) * .72;
    }
    if (z < BALL) {
      z = BALL;
      vz = Math.abs(vz) * .72;
    }
    if (z > 26 - BALL) {
      z = 26 - BALL;
      vz = -Math.abs(vz) * .72;
    }
    for (Obstacle o : hole().obstacles) collide(o, dt);
    for (Obstacle water : hole().water)
      if (Math.abs(x - water.x) < water.w / 2 && Math.abs(z - water.z) < water.d / 2) {
        x = beforeX;
        z = beforeZ;
        vx = vz = 0;
        moving = false;
        holeStrokes++;
        message = "Water · +1 penalty · ball returned";
        checkStrokeLimit();
        revision++;
        return;
      }
    double speed = Math.hypot(vx, vz);
    if (Math.hypot(x - hole().cupX, z - hole().cupZ) < .62 && speed < 3) {
      x = hole().cupX;
      z = hole().cupZ;
      y = .05;
      vx = vz = 0;
      moving = false;
      finishHole("Holed in " + holeStrokes + "!");
      return;
    }
    double next = Math.max(0, speed - .65 * dt);
    if (speed > 0) {
      vx *= next / speed;
      vz *= next / speed;
    }
    if (next < .035) {
      moving = false;
      vx = vz = 0;
      message = "Ready for your next putt.";
      checkStrokeLimit();
      revision++;
    }
  }

  private void collide(Obstacle o, double dt) {
    double cx = o.center(time), dx = x - cx, dz = z - o.z, ex = o.w / 2 + BALL, ez = o.d / 2 + BALL;
    if (Math.abs(dx) >= ex || Math.abs(dz) >= ez) return;
    if (ex - Math.abs(dx) < ez - Math.abs(dz)) {
      double sign = dx >= 0 ? 1 : -1;
      x = cx + sign * ex;
      double wallVelocity = o.moving ? 4.2 * Math.cos(time * 1.4) : 0;
      if ((vx - wallVelocity) * sign < 0) vx = wallVelocity - (vx - wallVelocity) * .72;
    } else {
      double sign = dz >= 0 ? 1 : -1;
      z = o.z + sign * ez;
      if (vz * sign < 0) vz = -vz * .72;
    }
  }

  private void checkStrokeLimit() {
    if (holeStrokes >= 12) finishHole("12-stroke limit · hole complete");
  }

  private void finishHole(String s) {
    holeComplete = true;
    card[holeIndex] = holeStrokes;
    message = s;
    roundComplete = holeIndex == 2;
    revision++;
  }

  public synchronized void nextHole() {
    if (!holeComplete || roundComplete) return;
    holeIndex++;
    holeStrokes = 0;
    holeComplete = false;
    x = 0;
    z = 2;
    y = BALL;
    vx = vy = vz = 0;
    aim = 0;
    trail.clear();
    message = "Tap or adjust aim, then putt.";
    revision++;
  }

  public synchronized void resetBall() {
    if (!isMini() || moving || holeComplete || roundComplete) return;
    x = 0;
    z = 2;
    y = BALL;
    holeStrokes++;
    message = "Ball reset to tee · +1 penalty";
    checkStrokeLimit();
    revision++;
  }

  public int totalStrokes() {
    int n = 0;
    for (int v : card) n += v;
    return n;
  }

  public static Hole[] course(Mode mode) {
    if (mode == Mode.MEADOW)
      return new Hole[] {
        new Hole("Garden Island", 3, 0, 23).wall(0, 12, 3, 3),
        new Hole("The Slalom", 4, 0, 24).wall(-3, 9, 5, .8).wall(3, 17, 5, .8),
        new Hole("Windmill Crossing", 3, 0, 23).gate(13)
      };
    return new Hole[] {
      new Hole("Castle Courtyard", 3, 3.6, 23).wall(0, 13, 2, 10),
      new Hole("The Moat", 4, 0, 23).water(-2, 12, 8, 3).wall(3.8, 19, 3, .6),
      new Hole("Royal Gates", 4, -3.5, 23).wall(-3.8, 8, 4, .8).gate(14).wall(3.8, 20, 4, .8)
    };
  }
}
