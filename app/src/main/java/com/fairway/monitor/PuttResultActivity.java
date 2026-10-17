package com.fairway.monitor;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Results use only the short, observed camera segment, never inferred total rollout. */
public class PuttResultActivity extends Activity {
  private static final int BG = 0xff101a18,
      PANEL = 0xff1b2823,
      GREEN = 0xffcbf078,
      TEXT = 0xfff2f5eb,
      MUTED = 0xffa8bbb0;

  public static Intent intent(Context context, LiveShotDetector.Reading reading, BallType type) {
    Intent i = new Intent(context, PuttResultActivity.class);
    i.putExtra("speed", reading.speed);
    i.putExtra("distance", reading.observedDistance);
    i.putExtra("fit", reading.fit);
    i.putExtra("ball", type.label);
    // Match the observations used by the detector's fit (excluding the pre-impact anchor).
    int count = Math.min(8, reading.path.size()) - 1;
    float[] xy = new float[count * 2];
    for (int n = 0; n < count; n++) {
      LiveShotDetector.Point p = reading.path.get(n + 1);
      xy[n * 2] = (float) p.x;
      xy[n * 2 + 1] = (float) p.y;
    }
    i.putExtra("path", xy);
    i.putExtra("duration", (reading.path.get(count).ns - reading.path.get(1).ns) / 1e9);
    return i;
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private void card(LinearLayout body, String label, String value, String note) {
    LinearLayout c = new LinearLayout(this);
    c.setOrientation(LinearLayout.VERTICAL);
    c.setPadding(dp(18), dp(14), dp(18), dp(14));
    GradientDrawable bg = new GradientDrawable();
    bg.setColor(PANEL);
    bg.setCornerRadius(dp(16));
    c.setBackground(bg);
    c.addView(text(label, 12, MUTED));
    c.addView(text(value, 28, TEXT));
    c.addView(text(note, 13, MUTED));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(10);
    body.addView(c, lp);
  }

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    Intent i = getIntent();
    float[] path = i.getFloatArrayExtra("path");
    if (path == null || path.length < 4) {
      finish();
      return;
    }
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(BG);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20), dp(14), dp(20), dp(20));
    scroll.addView(body);
    setContentView(scroll);
    body.addView(text("PUTT CAPTURED", 13, GREEN));
    body.addView(text("Your putt", 30, TEXT));
    TextView sub = text(i.getStringExtra("ball") + " · experimental camera estimates", 13, MUTED);
    sub.setPadding(0, 0, 0, dp(12));
    body.addView(sub);
    Button next = new Button(this);
    next.setText("Next putt · return to camera");
    next.setOnClickListener(v -> finish());
    body.addView(next);
    double dx = path[path.length - 2] - path[0], dy = path[path.length - 1] - path[1];
    double heading = Math.toDegrees(Math.atan2(-dy, dx));
    if (heading < 0) heading += 360;
    card(
        body,
        "DIRECTION IN CAMERA VIEW",
        String.format(Locale.US, "%.1f°", heading),
        "0° right · 90° up · 180° left · 270° down. This is not an angle from an aim line.");
    body.addView(new PathView(path), new LinearLayout.LayoutParams(-1, dp(200)));
    double speed = i.getDoubleExtra("speed", 0), distance = i.getDoubleExtra("distance", 0);
    card(
        body,
        "BALL SPEED",
        String.format(Locale.US, "%.2f m/s", speed),
        String.format(
            Locale.US, "%.2f mph · speed over the first tracked segment", speed * 2.236936));
    card(
        body,
        "OBSERVED DISTANCE",
        String.format(Locale.US, "%.2f m", distance),
        String.format(
            Locale.US,
            "%.2f ft · straight-line travel during the tracked segment",
            distance * 3.28084));
    card(
        body,
        "FULL ROLLOUT DISTANCE",
        "Not measured",
        "Capture finishes before the ball stops. Observed distance is only a short part of your"
            + " putt.");
    card(
        body,
        "TRACK DETAILS",
        String.format(
            Locale.US, "%d points · %.2f s", path.length / 2, i.getDoubleExtra("duration", 0)),
        String.format(
            Locale.US,
            "Straight-line fit %.1f%% — fit quality is not measurement accuracy.",
            i.getDoubleExtra("fit", 0) * 100));
    body.addView(
        text(
            "Direction and trace follow the displayed camera image. Keep the phone fixed and level,"
                + " and calibrate width at the ball’s plane. Press Next putt or Back, then replace"
                + " the ball at the selected target and wait for READY.",
            14,
            MUTED));
  }

  private final class PathView extends View {
    final float[] points;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    PathView(float[] p) {
      super(PuttResultActivity.this);
      points = p;
      setContentDescription(
          "Observed putt trace in camera coordinates, start circle to finish arrow");
    }

    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float cx = getWidth() / 2f, cy = getHeight() / 2f;
      paint.setStrokeWidth(dp(1));
      paint.setColor(0xff344b40);
      c.drawLine(dp(16), cy, getWidth() - dp(16), cy, paint);
      c.drawLine(cx, dp(16), cx, getHeight() - dp(16), paint);
      float minX = points[0], maxX = minX, minY = points[1], maxY = minY;
      for (int n = 0; n < points.length; n += 2) {
        minX = Math.min(minX, points[n]);
        maxX = Math.max(maxX, points[n]);
        minY = Math.min(minY, points[n + 1]);
        maxY = Math.max(maxY, points[n + 1]);
      }
      float scale =
          Math.min(
              (getWidth() - dp(70)) / Math.max(1, maxX - minX),
              (getHeight() - dp(70)) / Math.max(1, maxY - minY));
      float ox = cx - (minX + maxX) * scale / 2, oy = cy - (minY + maxY) * scale / 2;
      paint.setColor(GREEN);
      paint.setStrokeWidth(dp(3));
      for (int n = 2; n < points.length; n += 2)
        c.drawLine(
            ox + points[n - 2] * scale,
            oy + points[n - 1] * scale,
            ox + points[n] * scale,
            oy + points[n + 1] * scale,
            paint);
      paint.setStyle(Paint.Style.STROKE);
      c.drawCircle(ox + points[0] * scale, oy + points[1] * scale, dp(6), paint);
      paint.setStyle(Paint.Style.FILL);
      int end = points.length - 2;
      float x = ox + points[end] * scale, y = oy + points[end + 1] * scale;
      double a = Math.atan2(points[end + 1] - points[end - 1], points[end] - points[end - 2]);
      for (double d : new double[] {-.55, .55})
        c.drawLine(
            x,
            y,
            x - dp(14) * (float) Math.cos(a + d),
            y - dp(14) * (float) Math.sin(a + d),
            paint);
      paint.setColor(MUTED);
      paint.setTextSize(dp(12));
      c.drawText("Observed path · enlarged to fit", dp(12), getHeight() - dp(5), paint);
    }
  }
}
