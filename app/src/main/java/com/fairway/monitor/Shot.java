package com.fairway.monitor;

/** Canonical units are yards, mph, degrees and rpm. */
public final class Shot {
  public final String club, source, ballType;
  public final double carry, speed, launch, spin, offline;
  public final long time;

  public Shot(
      String club,
      String source,
      double carry,
      double speed,
      double launch,
      double spin,
      double offline,
      long time) {
    this(club, source, carry, speed, launch, spin, offline, time, "Normal ball");
  }

  public Shot(
      String club,
      String source,
      double carry,
      double speed,
      double launch,
      double spin,
      double offline,
      long time,
      String ballType) {
    if (!valid(carry, 1, 450)
        || !valid(speed, 1, 250)
        || !valid(launch, 0, 70)
        || !valid(spin, 0, 15000)
        || !valid(offline, -150, 150))
      throw new IllegalArgumentException("Enter realistic values within the displayed ranges.");
    this.club = club;
    this.source = source;
    this.ballType = BallType.from(ballType).label;
    this.carry = carry;
    this.speed = speed;
    this.launch = launch;
    this.spin = spin;
    this.offline = offline;
    this.time = time;
  }

  private static boolean valid(double n, double min, double max) {
    return Double.isFinite(n) && n >= min && n <= max;
  }

  public static double distance(double yards, boolean metric) {
    return metric ? yards * 0.9144 : yards;
  }

  public static double speed(double mph, boolean metric) {
    return metric ? mph * 1.609344 : mph;
  }

  public String csv() {
    return time + "," + club + "," + source + "," + carry + "," + speed + "," + launch + "," + spin
        + "," + offline + "," + ballType;
  }
}
