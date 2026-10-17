package com.fairway.monitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.util.concurrent.*;

/** Opt-in, foreground shot publishing. Never retries, replays history or uploads camera images. */
public final class SimulatorConnector {
  private static final ThreadPoolExecutor WORK =
      new ThreadPoolExecutor(
          1,
          1,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<Runnable>(4),
          r -> {
            Thread t = new Thread(r, "fairway-simulator");
            t.setDaemon(true);
            return t;
          });
  private static volatile SimTcp link = new SimTcp();
  private static volatile int generation;

  private SimulatorConnector() {}

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("fairway_simulator", 0);
  }

  public static void settingsChanged() {
    generation++;
    WORK.getQueue().clear();
    link.cancel();
    link = new SimTcp();
  }

  public static synchronized void publish(
      Context context, LiveShotDetector.Reading reading, BallType ball) {
    Context c = context.getApplicationContext();
    SharedPreferences p = prefs(c);
    if (!p.getBoolean("enabled", false)) return;
    long number = p.getLong("sequence", 0) + 1;
    p.edit().putLong("sequence", number).apply();
    try {
      SimProtocol.Reading r =
          new SimProtocol.Reading(
              number,
              System.currentTimeMillis(),
              reading.mode == LiveShotDetector.Mode.PUTT,
              reading.speed,
              reading.angle,
              ball);
      SimProtocol.Target target = SimProtocol.Target.from(p.getString("target", "RAW"));
      String payload = SimProtocol.encode(target, r, assumptions(p));
      p.edit()
          .putString("last_payload", payload)
          .putString("last_raw", SimProtocol.encode(SimProtocol.Target.RAW, r, assumptions(p)))
          .apply();
      send(c, target, payload, false);
    } catch (IllegalArgumentException e) {
      status(c, e.getMessage(), true);
    }
  }

  static SimProtocol.Assumptions assumptions(SharedPreferences p) {
    return new SimProtocol.Assumptions(
        p.getBoolean("assumptions", false),
        p.getBoolean("invert", false),
        Double.parseDouble(p.getString("hla", "0")),
        Double.parseDouble(p.getString("spin", "0")),
        Double.parseDouble(p.getString("axis", "0")));
  }

  static void test(Context context) {
    Context c = context.getApplicationContext();
    send(c, SimProtocol.Target.from(prefs(c).getString("target", "RAW")), null, true);
  }

  private static void send(Context c, SimProtocol.Target target, String payload, boolean test) {
    SharedPreferences p = prefs(c);
    String host = p.getString("host", "").trim();
    int port = p.getInt("port", target.port);
    final int ticket = generation;
    final SimTcp client = link;
    final long queuedAt = System.nanoTime();
    status(c, test ? "Testing TCP connection…" : "Sending latest shot…", false);
    try {
      WORK.execute(
          () -> {
            if (ticket != generation || (!test && !p.getBoolean("enabled", false))) return;
            if (!test && System.nanoTime() - queuedAt > 5000000000L) {
              status(c, "Shot not sent: connection queue was busy", true);
              return;
            }
            String message;
            try {
              if (test) {
                client.connect(host, port);
                message = "TCP connected; no test shot sent. Simulator play is not yet verified.";
              } else message = client.send(host, port, target, payload);
            } catch (Exception e) {
              message =
                  "Connection failed / delivery unknown: "
                      + e.getMessage()
                      + ". No automatic resend.";
            }
            if (ticket == generation) status(c, message, true);
          });
    } catch (RejectedExecutionException e) {
      status(c, "Shot not sent: connection queue is full", true);
    }
  }

  private static void status(Context c, String text, boolean toast) {
    prefs(c).edit().putString("status", text).apply();
    if (toast)
      new Handler(Looper.getMainLooper())
          .post(() -> Toast.makeText(c, text, Toast.LENGTH_LONG).show());
  }
}
