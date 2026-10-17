package com.fairway.monitor;

import java.util.Locale;

/** Wire formats only. Missing measurements must never silently become measured zeros. */
public final class SimProtocol {
  private SimProtocol() {}

  public enum Target {
    OPENSHOT("OpenShotGolf (open source)", 49152),
    HECKLE("HeckleGolfSim (open source)", 49152),
    OPENCONNECT("GSPro / Open Connect receiver", 921),
    OGS("OpenGolfSim", 3111),
    RAW("Custom receiver (Fairway JSON)", 2483);
    public final String label;
    public final int port;

    Target(String label, int port) {
      this.label = label;
      this.port = port;
    }

    public boolean openConnect() {
      return this == OPENSHOT || this == HECKLE || this == OPENCONNECT;
    }

    public static Target from(String name) {
      try {
        return valueOf(name);
      } catch (Exception ignored) {
        return RAW;
      }
    }
  }

  public static final class Reading {
    public final long number, time;
    public final boolean putt;
    public final double speed, angle;
    public final BallType ball;

    public Reading(
        long number, long time, boolean putt, double speed, double angle, BallType ball) {
      range(speed, 0.01, 150, "Speed");
      range(angle, -90, 90, "Angle");
      if (number < 1 || ball == null) throw new IllegalArgumentException("Invalid shot identity");
      this.number = number;
      this.time = time;
      this.putt = putt;
      this.speed = speed;
      this.angle = angle;
      this.ball = ball;
    }
  }

  public static final class Assumptions {
    public final boolean allowed, invertPutt;
    public final double swingHla, spin, spinAxis;

    public Assumptions(
        boolean allowed, boolean invertPutt, double swingHla, double spin, double spinAxis) {
      range(swingHla, -45, 45, "Assumed swing direction");
      range(spin, 0, 15000, "Assumed spin");
      range(spinAxis, -45, 45, "Assumed spin axis");
      this.allowed = allowed;
      this.invertPutt = invertPutt;
      this.swingHla = swingHla;
      this.spin = spin;
      this.spinAxis = spinAxis;
    }
  }

  public static String encode(Target target, Reading r, Assumptions a) {
    if (target == Target.RAW)
      return String.format(
          Locale.US,
          "{\"schema\":\"fairway.shot.v1\",\"shot_id\":%d,\"time_ms\":%d,\"source\":\"camera_experimental\","
              + "\"mode\":\"%s\",\"ball_type\":\"%s\",\"image_plane_speed_mps\":%.6f,"
              + "\"image_angle_deg\":%.6f,\"angle_kind\":\"%s\",\"spin_rpm\":null,\"carry_m\":null,"
              + "\"three_dimensional_launch\":null}",
          r.number,
          r.time,
          r.putt ? "putt" : "swing",
          r.ball.name(),
          r.speed,
          r.angle,
          r.putt ? "overhead_direction" : "side_view_launch");
    if (!a.allowed)
      throw new IllegalArgumentException(
          "Blocked: this simulator needs unmeasured spin/3D fields. Enable game assumptions or use"
              + " Fairway JSON.");
    double hla = r.putt ? (a.invertPutt ? -r.angle : r.angle) : a.swingHla;
    double vla = r.putt ? 0 : r.angle;
    range(hla, -45, 45, "Direction outside simulator range");
    range(vla, 0, 45, "Launch outside simulator range");
    // Putts use explicitly disclosed zero launch/spin assumptions, not measurements.
    double spin = r.putt ? 0 : a.spin, axis = r.putt ? 0 : a.spinAxis;
    if (target == Target.OGS)
      return String.format(
          Locale.US,
          "{\"type\":\"shot\",\"unit\":\"metric\",\"shot\":{\"ballSpeed\":%.6f,"
              + "\"verticalLaunchAngle\":%.6f,\"horizontalLaunchAngle\":%.6f,\"spinSpeed\":%.6f,\"spinAxis\":%.6f}}",
          r.speed,
          vla,
          hla,
          spin,
          axis);
    return String.format(
        Locale.US,
        "{\"DeviceID\":\"Fairway Camera GAME ASSUMPTIONS\",\"Units\":\"Yards\",\"ShotNumber\":%d,"
            + "\"APIversion\":\"1\",\"BallData\":{\"Speed\":%.6f,\"SpinAxis\":%.6f,\"TotalSpin\":%.6f,"
            + "\"HLA\":%.6f,\"VLA\":%.6f},\"ShotDataOptions\":{\"ContainsBallData\":true,"
            + "\"ContainsClubData\":false,\"IsHeartBeat\":false}}",
        r.number,
        r.speed / 0.44704,
        axis,
        spin,
        hla,
        vla);
  }

  private static void range(double n, double lo, double hi, String field) {
    if (!Double.isFinite(n) || n < lo || n > hi)
      throw new IllegalArgumentException(field + " must be " + lo + " to " + hi);
  }
}
