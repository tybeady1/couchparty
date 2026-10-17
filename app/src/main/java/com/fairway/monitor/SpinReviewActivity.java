package com.fairway.monitor;

import android.app.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Review original encoded frames; no interpolation and no retimed imported clips. */
@android.annotation.TargetApi(28)
public class SpinReviewActivity extends Activity {
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private TextView status;
  private SeekBar frameBar;
  private EditText ceiling;
  private CheckBox aligned;
  private Button analyze;
  private FrameView view;
  private String file;
  private int frames, index, requested;
  private double fps;
  private boolean busy;
  private volatile boolean dead;
  private Bitmap shown;
  private double targetX = -1, targetY = -1;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    file = getIntent().getStringExtra("file");
    requested = getIntent().getIntExtra("fps", 0);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(24, 12, 24, 12);
    setContentView(body);
    TextView title = new TextView(this);
    title.setText("Spin review · experimental");
    title.setTextSize(22);
    body.addView(title);
    view = new FrameView();
    body.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
    frameBar = new SeekBar(this);
    frameBar.setEnabled(false);
    body.addView(frameBar);
    frameBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int p, boolean u) {}

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {
            if (!busy) load(b.getProgress());
          }
        });
    ceiling = new EditText(this);
    ceiling.setInputType(2);
    ceiling.setHint("Expected maximum RPM (must be ≤ fps × 10)");
    ceiling.setText("1000");
    body.addView(ceiling);
    aligned = new CheckBox(this);
    aligned.setText(
        "Spin axis faces camera; one red dot stays visible; actual spin is below this ceiling");
    body.addView(aligned);
    analyze = new Button(this);
    analyze.setText("Analyze from selected frame");
    analyze.setEnabled(false);
    analyze.setOnClickListener(v -> analyze());
    body.addView(analyze);
    status = new TextView(this);
    status.setText("Checking encoded frame timing…");
    body.addView(status);
    Button back = new Button(this);
    back.setText("Back to capture");
    back.setOnClickListener(v -> finish());
    body.addView(back);
    body.removeView(view);
    body.removeView(frameBar);
    LinearLayout outer = new LinearLayout(this), left = new LinearLayout(this);
    left.setOrientation(LinearLayout.VERTICAL);
    setContentView(outer);
    outer.addView(left, new LinearLayout.LayoutParams(0, -1, 1));
    left.addView(view, new LinearLayout.LayoutParams(-1, 0, 1));
    left.addView(frameBar);
    ScrollView panel = new ScrollView(this);
    panel.addView(body);
    outer.addView(
        panel,
        new LinearLayout.LayoutParams(
            (int) (300 * getResources().getDisplayMetrics().density), -1));
    worker.execute(
        () -> {
          try {
            checkTiming();
            ui(
                () -> {
                  frameBar.setMax(Math.max(0, frames - 6));
                  frameBar.setEnabled(true);
                  ceiling.setText(String.valueOf(Math.min(1000, requested * 10)));
                  load(0);
                });
          } catch (Exception e) {
            ui(() -> status.setText("Cannot analyze: " + e.getMessage()));
          }
        });
  }

  private void ui(Runnable r) {
    runOnUiThread(
        () -> {
          if (!dead && !isFinishing()) r.run();
        });
  }

  private void checkTiming() throws Exception {
    if (Build.VERSION.SDK_INT < 28) throw new Exception("Android 9+ required");
    MediaExtractor e = new MediaExtractor();
    try {
      e.setDataSource(file);
      int track = -1;
      for (int n = 0; n < e.getTrackCount(); n++)
        if (e.getTrackFormat(n).getString(MediaFormat.KEY_MIME).startsWith("video/")) {
          track = n;
          break;
        }
      if (track < 0) throw new Exception("No video stream");
      e.selectTrack(track);
      ArrayList<Long> gaps = new ArrayList<>();
      long last = -1;
      frames = 0;
      while (e.getSampleTime() >= 0 && frames < 2000) {
        long pts = e.getSampleTime();
        if (last >= 0) gaps.add(pts - last);
        last = pts;
        frames++;
        if (!e.advance()) break;
      }
      if (frames < 6 || gaps.isEmpty()) throw new Exception("Too few encoded frames");
      ArrayList<Long> sorted = new ArrayList<>(gaps);
      Collections.sort(sorted);
      long median = sorted.get(sorted.size() / 2);
      if (median <= 0) throw new Exception("Non-monotonic video timestamps");
      fps = 1000000.0 / median;
      if (Math.abs(fps - requested) > requested * .05)
        throw new Exception(
            "Encoded timing does not match requested capture rate. No RPM computed.");
      for (long gap : gaps)
        if (Math.abs(gap - median) > median * .25)
          throw new Exception(
              "Dropped/irregular frames detected. Retry a shorter, brighter capture.");
    } finally {
      e.release();
    }
  }

  private Bitmap bitmap(MediaMetadataRetriever r, int frame) {
    Bitmap original = r.getFrameAtIndex(frame);
    if (original == null) throw new IllegalArgumentException("Frame unavailable");
    int w = original.getWidth(), h = original.getHeight();
    if (Math.max(w, h) > 960) {
      float scale = 960f / Math.max(w, h);
      Bitmap small =
          Bitmap.createScaledBitmap(original, Math.round(w * scale), Math.round(h * scale), true);
      original.recycle();
      return small;
    }
    return original;
  }

  private void load(int frame) {
    busy = true;
    analyze.setEnabled(false);
    frameBar.setEnabled(false);
    worker.execute(
        () -> {
          MediaMetadataRetriever r = new MediaMetadataRetriever();
          try {
            r.setDataSource(file);
            Bitmap b = bitmap(r, frame);
            ui(
                () -> {
                  if (shown != null) shown.recycle();
                  shown = b;
                  index = frame;
                  targetX = targetY = -1;
                  view.invalidate();
                  status.setText(
                      String.format(
                          Locale.US,
                          "Frame %d / %d · %.1f encoded fps. Choose a frame just before rotation,"
                              + " then tap the ball. Orange crosshair selects it.",
                          frame,
                          frames,
                          fps));
                });
          } catch (Exception e) {
            ui(() -> status.setText(e.getMessage()));
          } finally {
            try {
              r.release();
            } catch (Exception ignored) {
            }
            ui(
                () -> {
                  busy = false;
                  frameBar.setEnabled(true);
                  analyze.setEnabled(true);
                });
          }
        });
  }

  private void analyze() {
    if (busy || shown == null) return;
    if (targetX < 0) {
      status.setText("Tap the marked ball in the review image first.");
      return;
    }
    double max;
    try {
      max = Double.parseDouble(ceiling.getText().toString());
      if (max <= 0 || max > fps * 10) throw new Exception();
    } catch (Exception e) {
      ceiling.setError("Enter 1–" + (int) (fps * 10) + " RPM");
      return;
    }
    if (!aligned.isChecked()) {
      status.setText(
          "Confirm axis/marker setup and spin ceiling first. A high-speed alias cannot be ruled out"
              + " by software alone.");
      return;
    }
    final double limit = max, tx = targetX, ty = targetY;
    final int start = index;
    busy = true;
    analyze.setEnabled(false);
    frameBar.setEnabled(false);
    status.setText("Analyzing up to 240 original frames…");
    worker.execute(
        () -> {
          MediaMetadataRetriever r = new MediaMetadataRetriever();
          try {
            r.setDataSource(file);
            double x = tx * shown.getWidth(),
                y = ty * shown.getHeight(),
                radius = 70,
                lastAngle = 0;
            boolean started = false;
            ArrayList<Double> angles = new ArrayList<>();
            int first = -1;
            for (int n = start; n < Math.min(frames, start + 240) && !dead; n++) {
              Bitmap b = bitmap(r, n);
              int[] px = new int[b.getWidth() * b.getHeight()];
              b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
              SpinTracker.Marker mark =
                  SpinTracker.locate(
                      px, b.getWidth(), b.getHeight(), x, y, Math.max(60, radius * 3));
              b.recycle();
              if (mark == null) {
                if (started) break;
                angles.clear();
                continue;
              }
              if (started && Math.abs(mark.r - radius) > radius * .2) break;
              x = mark.x;
              y = mark.y;
              radius = mark.r;
              if (angles.isEmpty()) {
                angles.add(mark.angle);
                lastAngle = mark.angle;
                first = n;
                continue;
              }
              double delta = mark.angle - lastAngle;
              while (delta > 180) delta -= 360;
              while (delta < -180) delta += 360;
              if (!started && Math.abs(delta) < 2) {
                angles.clear();
                angles.add(mark.angle);
                first = n;
              } else {
                started = true;
                angles.add(mark.angle);
              }
              lastAngle = mark.angle;
            }
            double[] a = new double[angles.size()];
            for (int n = 0; n < a.length; n++) a[n] = angles.get(n);
            SpinTracker.Result result = SpinTracker.fit(a, fps, limit);
            final int begin = first;
            ui(
                () ->
                    status.setText(
                        String.format(
                            Locale.US,
                            "APPARENT ROTATION: %.0f RPM · %s in recorded image\n"
                                + "%d consecutive frames from frame %d · %.0f° fitted rotation\n"
                                + "Experimental, axis-aligned estimate only. Not total spin or a 3D"
                                + " spin axis. Faster actual spin may alias. Not sent to"
                                + " simulators.",
                            Math.abs(result.rpm),
                            result.rpm > 0 ? "counterclockwise" : "clockwise",
                            result.points,
                            begin,
                            result.span)));
          } catch (Exception e) {
            ui(
                () ->
                    status.setText(
                        "No reliable spin estimate: "
                            + e.getMessage()
                            + "\n"
                            + "Use a larger, sharper ball image, one red dot and a camera-facing"
                            + " spin axis."));
          } finally {
            try {
              r.release();
            } catch (Exception ignored) {
            }
            ui(
                () -> {
                  busy = false;
                  analyze.setEnabled(true);
                  frameBar.setEnabled(true);
                });
          }
        });
  }

  protected void onDestroy() {
    dead = true;
    worker.shutdownNow();
    super.onDestroy();
  }

  private class FrameView extends View {
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    RectF rect = new RectF();

    FrameView() {
      super(SpinReviewActivity.this);
      setBackgroundColor(0xff101a18);
      setClickable(true);
      setContentDescription("Captured frame: tap marked ball");
    }

    protected void onDraw(Canvas c) {
      if (shown == null) return;
      float
          s =
              Math.min(
                  getWidth() / (float) shown.getWidth(), getHeight() / (float) shown.getHeight()),
          w = shown.getWidth() * s,
          h = shown.getHeight() * s;
      rect.set(
          (getWidth() - w) / 2, (getHeight() - h) / 2, (getWidth() + w) / 2, (getHeight() + h) / 2);
      c.drawBitmap(shown, null, rect, p);
      if (targetX >= 0) {
        p.setColor(0xffffbb55);
        p.setStrokeWidth(4);
        p.setStyle(Paint.Style.STROKE);
        c.drawCircle(rect.left + (float) targetX * w, rect.top + (float) targetY * h, 24, p);
        p.setStyle(Paint.Style.FILL);
      }
    }

    public boolean onTouchEvent(MotionEvent e) {
      if (e.getActionMasked() == MotionEvent.ACTION_DOWN
          && !busy
          && shown != null
          && rect.contains(e.getX(), e.getY())) {
        targetX = (e.getX() - rect.left) / rect.width();
        targetY = (e.getY() - rect.top) / rect.height();
        invalidate();
      }
      if (e.getActionMasked() == MotionEvent.ACTION_UP) performClick();
      return true;
    }

    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
