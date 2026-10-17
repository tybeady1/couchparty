package com.fairway.monitor;

public enum BallType {
  NORMAL("Normal ball", .42),
  RANGE("Range ball", .38),
  FOAM("Foam ball", .32),
  WIFFLE("Wiffle ball", .22);
  public final String label;
  public final double minimumFill;

  BallType(String label, double fill) {
    this.label = label;
    minimumFill = fill;
  }

  public static BallType from(String value) {
    for (BallType b : values()) if (b.label.equals(value)) return b;
    return NORMAL;
  }

  public static String[] labels() {
    BallType[] all = values();
    String[] labels = new String[all.length];
    for (int i = 0; i < all.length; i++) labels[i] = all[i].label;
    return labels;
  }
}
