package com.fairway.monitor;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

public class CameraActivity extends Activity {
  private final int BG = 0xff101a18,
      CARD = 0xff1b2823,
      GREEN = 0xffcbf078,
      TEXT = 0xfff2f5eb,
      MUTED = 0xff9daea0;
  private LinearLayout content;
  private EditText scale, offset;
  private TextView status;
  private LinearLayout resultBox;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private volatile boolean cancelled = false;
  private boolean busy = false;
  private double selectedScale = 2, selectedOffset = 0;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (state != null) {
      selectedScale = state.getDouble("scale", 2);
      selectedOffset = state.getDouble("offset", 0);
    }
    screen();
  }

  @Override
  public void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putDouble("scale", selectedScale);
    b.putDouble("offset", selectedOffset);
  }

  private int dp(int n) {
    return (int) (n * getResources().getDisplayMetrics().density + .5f);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private void gap(int n) {
    Space s = new Space(this);
    content.addView(s, new LinearLayout.LayoutParams(1, dp(n)));
  }

  private void button(String s, Runnable action) {
    TextView t = text(s, 15, BG);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(12), dp(16), dp(12), dp(16));
    GradientDrawable d = new GradientDrawable();
    d.setColor(GREEN);
    d.setCornerRadius(dp(14));
    t.setBackground(d);
    t.setOnClickListener(
        v -> {
          if (!busy) action.run();
        });
    content.addView(t, new LinearLayout.LayoutParams(-1, -2));
    gap(12);
  }

  private EditText field(String label, String value) {
    content.addView(text(label, 13, MUTED));
    EditText e = new EditText(this);
    e.setText(value);
    e.setSingleLine();
    e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    content.addView(e);
    gap(12);
    return e;
  }

  private void screen() {
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(BG);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(dp(22), dp(20), dp(22), dp(28));
    scroll.addView(content);
    setContentView(scroll);
    TextView back = text("‹  Back to practice", 14, GREEN);
    back.setPadding(0, dp(8), 0, dp(16));
    back.setOnClickListener(v -> finish());
    content.addView(back);
    content.addView(text("Camera lab", 30, TEXT));
    gap(6);
    content.addView(text("EXPERIMENTAL · IMAGE-PLANE ESTIMATES", 10, GREEN));
    gap(20);
    content.addView(text("Set up a side-on view", 19, TEXT));
    gap(8);
    content.addView(
        text(
            "Keep the phone level and fixed on a tripod, perpendicular to the shot. Use bright"
                + " light and a dark, still background. Keep the white ball visible after impact."
                + " Prefer 120–240 fps; normal video often misses fast shots.",
            14,
            MUTED));
    gap(16);
    content.addView(
        text(
            "Measure the full frame’s horizontal width at the ball’s flight plane. This is the"
                + " scale for the estimate, not the camera-to-ball distance. Avoid zoom changes and"
                + " slow-motion exports that alter playback timing.",
            14,
            MUTED));
    gap(16);
    scale = field("Visible frame width at ball plane (metres)", String.valueOf(selectedScale));
    offset =
        field(
            "Start analysis at clip time (seconds, just before impact)",
            String.valueOf(selectedOffset));
    button(
        "Open live camera monitor",
        () -> startActivity(new Intent(this, LiveCameraActivity.class)));
    button("Import a video clip", () -> choose(false));
    status =
        text(
            "Analyzes up to 240 frames within 2 seconds of your chosen start time. All processing"
                + " stays on this device.",
            13,
            MUTED);
    content.addView(status);
    gap(18);
    resultBox = new LinearLayout(this);
    resultBox.setOrientation(LinearLayout.VERTICAL);
    content.addView(resultBox);
    String last = getSharedPreferences("fairway", 0).getString("cameraResult", null);
    if (last != null) {
      resultBox.addView(text("LAST CAMERA ESTIMATE", 11, GREEN));
      resultBox.addView(text(last, 15, TEXT));
    }
  }

  private void choose(boolean record) {
    try {
      selectedScale = Double.parseDouble(scale.getText().toString());
      selectedOffset = Double.parseDouble(offset.getText().toString());
      if (!Double.isFinite(selectedScale)
          || selectedScale < .1
          || selectedScale > 50
          || !Double.isFinite(selectedOffset)
          || selectedOffset < 0
          || selectedOffset > 3600) throw new IllegalArgumentException();
    } catch (Exception e) {
      status.setText("Enter frame width from 0.1 to 50 m and start time from 0 to 3600 s.");
      return;
    }
    Intent i;
    if (record) {
      i = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
      i.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
      i.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10);
    } else {
      i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.setType("video/*");
    }
    try {
      startActivityForResult(i, record ? 71 : 72);
    } catch (ActivityNotFoundException e) {
      status.setText(
          "No camera app is available. Import a clip recorded with your phone’s camera.");
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if ((request == 71 || request == 72)
        && result == RESULT_OK
        && data != null
        && data.getData() != null) analyze(data.getData());
  }

  private void analyze(Uri uri) {
    busy = true;
    status.setText("Analyzing video… keep this screen open.");
    resultBox.removeAllViews();
    final double metres = selectedScale, start = selectedOffset;
    final String selectedBall =
        BallType.from(getSharedPreferences("fairway", 0).getString("ballType", "Normal ball"))
            .label;
    worker.execute(
        () -> {
          MediaMetadataRetriever retriever = new MediaMetadataRetriever();
          MediaExtractor extractor = new MediaExtractor();
          Bitmap preview = null;
          try {
            retriever.setDataSource(this, uri);
            extractor.setDataSource(this, uri, null);
            int video = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
              String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
              if (mime != null && mime.startsWith("video/")) {
                video = i;
                break;
              }
            }
            if (video < 0) throw new IllegalArgumentException("No video track found.");
            extractor.selectTrack(video);
            long begin = (long) (start * 1e6);
            extractor.seekTo(begin, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            TreeSet<Long> timestamps = new TreeSet<>();
            while (!cancelled) {
              long t = extractor.getSampleTime();
              if (t < 0 || t > begin + 2000000) break;
              if (t >= begin) timestamps.add(t);
              if (timestamps.size() >= 240 || !extractor.advance()) break;
            }
            if (timestamps.size() < 4)
              throw new IllegalArgumentException(
                  "Too few frames. Choose a start time before impact.");
            BallTracker tracker = new BallTracker();
            int count = 0, w = 0, h = 0;
            for (long t : timestamps) {
              if (cancelled) break;
              Bitmap frame = retriever.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST);
              if (frame == null) continue;
              int targetW = Math.min(640, frame.getWidth()),
                  targetH =
                      Math.max(
                          1, Math.round(frame.getHeight() * targetW / (float) frame.getWidth()));
              Bitmap small = Bitmap.createScaledBitmap(frame, targetW, targetH, true);
              if (small != frame) frame.recycle();
              w = small.getWidth();
              h = small.getHeight();
              int[] pixels = new int[w * h];
              small.getPixels(pixels, 0, w, 0, 0, w, h);
              tracker.frame(pixels, w, h, t);
              if (preview != null) preview.recycle();
              preview = small;
              count++;
              if (count % 15 == 0) {
                final int done = count;
                runOnUiThread(
                    () ->
                        status.setText(
                            "Analyzing frame " + done + " / " + timestamps.size() + "…"));
              }
            }
            if (cancelled) return;
            BallTracker.Result result = tracker.result(metres);
            if (result == null)
              throw new IllegalArgumentException(
                  "No consistent ball track found. Try a closer, brighter side-on clip with a still"
                      + " background and a start time just before impact. No measurement was"
                      + " saved.");
            // Use a frame from the detected interval for a reviewable trail overlay.
            Bitmap trackFrame =
                retriever.getFrameAtTime(
                    result.points.get(result.points.size() - 1).us,
                    MediaMetadataRetriever.OPTION_CLOSEST);
            if (trackFrame != null) {
              Bitmap small = Bitmap.createScaledBitmap(trackFrame, w, h, true);
              if (preview != null) preview.recycle();
              preview = small;
              if (small != trackFrame) trackFrame.recycle();
            }
            Bitmap annotated = preview.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(annotated);
            Paint p = new Paint(3);
            p.setColor(GREEN);
            p.setStrokeWidth(3);
            BallTracker.Point previous = null;
            for (BallTracker.Point q : result.points) {
              if (previous != null)
                canvas.drawLine(
                    (float) previous.x, (float) previous.y, (float) q.x, (float) q.y, p);
              canvas.drawCircle((float) q.x, (float) q.y, 5, p);
              previous = q;
            }
            final double fps =
                (timestamps.size() - 1) * 1e6 / (timestamps.last() - timestamps.first());
            String report =
                selectedBall
                    + "\n"
                    + String.format(
                        Locale.US,
                        "Visible speed  %.1f mph  /  %.1f km/h\n"
                            + "Image-plane launch angle  %.1f°\n"
                            + "%d tracked points · %.0f fps sample timing\n"
                            + "Scale  %.2f m across frame\n"
                            + "Carry and spin: unavailable",
                        result.metresPerSecond * 2.236936,
                        result.metresPerSecond * 3.6,
                        result.angle,
                        result.points.size(),
                        fps,
                        metres);
            runOnUiThread(
                () -> {
                  if (isFinishing() || isDestroyed()) {
                    annotated.recycle();
                    return;
                  }
                  busy = false;
                  status.setText(
                      "Candidate track found — verify the green points follow the ball. A moving"
                          + " bright object can produce a false match.");
                  ImageView image = new ImageView(this);
                  image.setAdjustViewBounds(true);
                  image.setImageBitmap(annotated);
                  image.setContentDescription(
                      "Video frame with detected ball positions marked in green");
                  resultBox.addView(image, new LinearLayout.LayoutParams(-1, -2));
                  resultBox.addView(text(report, 17, TEXT));
                  TextView save = text("Save reviewed estimate", 16, BG);
                  save.setGravity(Gravity.CENTER);
                  save.setPadding(dp(12), dp(16), dp(12), dp(16));
                  save.setBackgroundColor(GREEN);
                  resultBox.addView(save);
                  save.setOnClickListener(
                      v -> {
                        getSharedPreferences("fairway", 0)
                            .edit()
                            .putString(
                                "cameraResult",
                                report
                                    + "\nReviewed "
                                    + new java.text.SimpleDateFormat(
                                            "MMM d, h:mm a", Locale.getDefault())
                                        .format(new Date()))
                            .apply();
                        save.setText("Estimate saved on this device");
                        save.setEnabled(false);
                      });
                  resultBox.addView(
                      text(
                          "Approximate 2D motion, not calibrated launch-monitor accuracy."
                              + " Perspective, blur, camera movement and slow-motion playback can"
                              + " distort results. A low frame rate may make a real golf shot"
                              + " untrackable. These estimates are kept separate from measured"
                              + " practice shots.",
                          13,
                          MUTED));
                });
          } catch (Exception e) {
            final String message =
                e instanceof IllegalArgumentException
                    ? e.getMessage()
                    : "This clip could not be decoded. Try an MP4 video from your camera.";
            runOnUiThread(
                () -> {
                  busy = false;
                  status.setText(message);
                });
          } finally {
            if (preview != null) preview.recycle();
            try {
              retriever.release();
            } catch (Exception ignored) {
            }
            extractor.release();
          }
        });
  }

  @Override
  protected void onDestroy() {
    cancelled = true;
    worker.shutdownNow();
    super.onDestroy();
  }
}
