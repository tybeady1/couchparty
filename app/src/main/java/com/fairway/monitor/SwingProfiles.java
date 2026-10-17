package com.fairway.monitor;

import android.content.*;
import org.json.*;

/** One current camera setup per ball type. Raw shot history is immutable. */
public final class SwingProfiles {
  static String key(BallType ball) {
    return "swingCalibration_" + ball.name();
  }

  static JSONArray samples(Context c, BallType b) {
    try {
      return new JSONArray(c.getSharedPreferences("fairway", 0).getString(key(b), "[]"));
    } catch (Exception e) {
      return new JSONArray();
    }
  }

  static boolean enabled(Context c, BallType b) {
    return c.getSharedPreferences("fairway", 0).getBoolean(key(b) + "Enabled", true);
  }

  public static double[] correct(Context c, BallType ball, double speed, double angle) {
    JSONArray a = samples(c, ball);
    if (!enabled(c, ball) || a.length() == 0) return new double[] {speed, angle, 0};
    double[] scales = new double[a.length()], offsets = new double[a.length()];
    try {
      for (int n = 0; n < a.length(); n++) {
        JSONObject j = a.getJSONObject(n);
        double[] pair =
            SwingCalibration.pair(
                j.getDouble("rawSpeed"),
                j.getDouble("rawAngle"),
                j.getDouble("referenceSpeed"),
                j.getDouble("referenceAngle"));
        scales[n] = pair[0];
        offsets[n] = pair[1];
      }
      double s = speed * SwingCalibration.median(scales, 1),
          v = angle + SwingCalibration.median(offsets, 0);
      if (s < .5 || s > 120 || v < 0 || v > 75) return new double[] {speed, angle, -1};
      return new double[] {s, v, a.length()};
    } catch (Exception e) {
      return new double[] {speed, angle, -1};
    }
  }

  static void save(
      Context c, BallType ball, String id, double speed, double angle, double rs, double ra)
      throws Exception {
    SwingCalibration.pair(speed, angle, rs, ra);
    JSONArray old = samples(c, ball), next = new JSONArray();
    for (int n = 0; n < old.length(); n++) {
      JSONObject j = old.getJSONObject(n);
      if (!id.equals(j.optString("id"))) next.put(j);
    }
    while (next.length() >= 10) next.remove(0);
    JSONObject j = new JSONObject();
    j.put("id", id);
    j.put("rawSpeed", speed);
    j.put("rawAngle", angle);
    j.put("referenceSpeed", rs);
    j.put("referenceAngle", ra);
    next.put(j);
    c.getSharedPreferences("fairway", 0)
        .edit()
        .putString(key(ball), next.toString())
        .putBoolean(key(ball) + "Enabled", true)
        .apply();
  }
}
