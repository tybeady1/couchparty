package com.fairway.monitor;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.text.InputType;
import android.util.Range;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** Live Camera2 preview and bounded, background image analysis. No video leaves the phone. */
public class LiveCameraActivity extends Activity {
  private static final int BG = 0xff101a18,
      PANEL = 0xff1b2823,
      GREEN = 0xffcbf078,
      TEXT = 0xfff2f5eb,
      MUTED = 0xff9daea0;
  private Preview preview;
  private TextView status,
      result,
      fpsLabel,
      modeButton,
      ballButton,
      pauseButton,
      scaleButton,
      historyButton;
  private LiveShotDetector.Mode mode = LiveShotDetector.Mode.PUTT;
  private BallType ballType = BallType.NORMAL;
  private double metres = 2, targetX = -1, targetY = -1;
  private boolean active, paused, requestedPermission, calibrated;
  private CameraRun camera;
  private JSONArray history = new JSONArray();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    android.content.SharedPreferences p = getSharedPreferences("fairway", 0);
    metres = p.getFloat("liveWidth", 2);
    ballType = BallType.from(p.getString("ballType", "Normal ball"));
    mode = p.getBoolean("putting", true) ? LiveShotDetector.Mode.PUTT : LiveShotDetector.Mode.SWING;
    if (getIntent().getBooleanExtra("gameInput", false))
      mode =
          getIntent().getBooleanExtra("gamePutting", true)
              ? LiveShotDetector.Mode.PUTT
              : LiveShotDetector.Mode.SWING;
    try {
      history = new JSONArray(p.getString("liveReadings", "[]"));
    } catch (JSONException ignored) {
    }
    if (b != null) {
      targetX = b.getDouble("x", -1);
      targetY = b.getDouble("y", -1);
      requestedPermission = b.getBoolean("asked");
      calibrated = b.getBoolean("calibrated");
    }
    screen();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putDouble("x", targetX);
    b.putDouble("y", targetY);
    b.putBoolean("asked", requestedPermission);
    b.putBoolean("calibrated", calibrated);
  }

  private int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    return v;
  }

  private TextView button(String s, Runnable action) {
    TextView v = text(s, 13, TEXT);
    v.setGravity(Gravity.CENTER);
    v.setMinHeight(dp(46));
    v.setPadding(dp(10), dp(8), dp(10), dp(8));
    GradientDrawable d = new GradientDrawable();
    d.setColor(PANEL);
    d.setCornerRadius(dp(12));
    v.setBackground(d);
    v.setOnClickListener(w -> action.run());
    return v;
  }

  private void add(LinearLayout p, View v) {
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(9);
    p.addView(v, lp);
  }

  private void screen() {
    LinearLayout root = new LinearLayout(this);
    root.setPadding(dp(12), dp(8), dp(12), dp(8));
    root.setBackgroundColor(BG);
    setContentView(root);
    LinearLayout left = new LinearLayout(this);
    left.setOrientation(LinearLayout.VERTICAL);
    LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -1, 1);
    leftParams.rightMargin = dp(14);
    root.addView(left, leftParams);
    LinearLayout bar = new LinearLayout(this);
    TextView back = button("‹ Back", this::finish);
    bar.addView(back);
    TextView title = text("  Live launch monitor", 20, TEXT);
    bar.addView(title);
    add(left, bar);
    preview = new Preview();
    left.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    status = text("Opening camera…", 14, GREEN);
    status.setMinHeight(dp(43));
    add(left, status);
    fpsLabel = text("LIVE CAMERA · experimental estimates", 11, MUTED);
    add(left, fpsLabel);
    ScrollView scroll = new ScrollView(this);
    root.addView(scroll, new LinearLayout.LayoutParams(dp(240), -1));
    LinearLayout controls = new LinearLayout(this);
    controls.setOrientation(LinearLayout.VERTICAL);
    scroll.addView(controls);
    modeButton =
        button(
            modeLabel(),
            () -> {
              mode =
                  mode == LiveShotDetector.Mode.PUTT
                      ? LiveShotDetector.Mode.SWING
                      : LiveShotDetector.Mode.PUTT;
              modeButton.setText(modeLabel());
              calibrated = false;
              targetX = -1;
              targetY = -1;
              scaleButton.setText(scaleLabel());
              persistSettings();
              resetDetector();
            });
    add(controls, modeButton);
    ballButton =
        button(
            ballType.label + " ▾",
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Ball type")
                    .setItems(
                        BallType.labels(),
                        (d, i) -> {
                          ballType = BallType.values()[i];
                          ballButton.setText(ballType.label + " ▾");
                          persistSettings();
                          resetDetector();
                        })
                    .show());
    add(controls, ballButton);
    scaleButton = button(scaleLabel(), this::calibrate);
    add(controls, scaleButton);
    add(
        controls,
        button(
            "Select ball at image center",
            () -> {
              if (preview.bitmap == null) {
                Toast.makeText(this, "Wait for the live camera image first.", Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              selectTarget(.5, .5);
            }));
    add(
        controls,
        button(
            "Phone setup guide", () -> SetupGuide.show(this, mode == LiveShotDetector.Mode.PUTT)));
    result =
        text(
            "Set the measured frame width, then tap the ball.\n"
                + "Wait for READY below the live view, then hit it.\n\n"
                + "Your next reading appears here.",
            16,
            TEXT);
    add(controls, result);
    pauseButton =
        button(
            "Pause detection",
            () -> {
              paused = !paused;
              pauseButton.setText(paused ? "Resume detection" : "Pause detection");
              resetDetector();
            });
    add(controls, pauseButton);
    historyButton = button("Readings (" + history.length() + ")", this::showHistory);
    add(controls, historyButton);
    add(
        controls,
        button(
            "Restart / enable camera",
            () -> {
              if (checkSelfPermission(Manifest.permission.CAMERA)
                  != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[] {Manifest.permission.CAMERA}, 91);
              } else restartCamera();
            }));
    add(
        controls,
        button(
            "Analyze a saved clip", () -> startActivity(new Intent(this, CameraActivity.class))));
    add(
        controls,
        text(
            "Use a light-colored ball against a darker background. Putts work at lower frame rates;"
                + " fast swings may leave the frame before enough points are captured. No carry or"
                + " spin is inferred.",
            11,
            MUTED));
  }

  private String modeLabel() {
    return mode == LiveShotDetector.Mode.PUTT ? "Putting · indoor  ⇄" : "Full swing  ⇄";
  }

  private String scaleLabel() {
    return calibrated
        ? String.format(Locale.US, "Frame width: %.2f m  ·  edit", metres)
        : "Set measured frame width";
  }

  private void persistSettings() {
    getSharedPreferences("fairway", 0)
        .edit()
        .putBoolean("putting", mode == LiveShotDetector.Mode.PUTT)
        .putFloat("liveWidth", (float) metres)
        .putString("ballType", ballType.label)
        .apply();
  }

  private void calibrate() {
    EditText e = new EditText(this);
    e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    e.setText(String.valueOf(metres));
    AlertDialog d =
        new AlertDialog.Builder(this)
            .setTitle("Calibrate visible width")
            .setMessage(
                "Measure the width of the camera image at the ball’s plane, in metres. Exclude"
                    + " black borders. For putts, mount the phone directly overhead and level."
                    + " Recalibrate if the phone moves or zoom changes.")
            .setView(e)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create();
    d.setOnShowListener(
        v ->
            d.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(
                    w -> {
                      try {
                        double n = Double.parseDouble(e.getText().toString());
                        if (!Double.isFinite(n) || n < .1 || n > 50)
                          throw new IllegalArgumentException();
                        metres = n;
                        calibrated = true;
                        persistSettings();
                        scaleButton.setText(scaleLabel());
                        resetDetector();
                        d.dismiss();
                      } catch (Exception ex) {
                        e.setError("Enter 0.1–50 metres");
                      }
                    }));
    d.show();
  }

  private void selectTarget(double x, double y) {
    targetX = x;
    targetY = y;
    resetDetector();
    preview.invalidate();
    Toast.makeText(this, "Target selected", Toast.LENGTH_SHORT).show();
    if (!calibrated) calibrate();
    else if (paused)
      Toast.makeText(this, "Tap Resume detection to lock the ball.", Toast.LENGTH_LONG).show();
  }

  private void resetDetector() {
    status.setText(
        paused
            ? "Detection paused — tap Resume detection."
            : !calibrated
                ? "Calibration required — set the measured frame width to enable detection."
                : targetX < 0
                    ? "Tap the stationary ball in the live view."
                    : "Target selected — hold the ball still while it locks.");
    if (camera != null) camera.configure();
  }

  @Override
  protected void onResume() {
    super.onResume();
    active = true;
    if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
      restartCamera();
    else {
      status.setText("Camera permission is needed for the live view.");
      if (!requestedPermission) {
        requestedPermission = true;
        requestPermissions(new String[] {Manifest.permission.CAMERA}, 91);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request == 91) {
      if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
        if (active) restartCamera();
      } else
        status.setText(
            "Camera access denied. Enable camera permission in Android app settings, then return.");
    }
  }

  private void restartCamera() {
    if (camera != null) camera.close();
    camera = new CameraRun();
    camera.start();
  }

  @Override
  protected void onPause() {
    active = false;
    if (camera != null) {
      camera.close();
      camera = null;
    }
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    preview.release();
    super.onDestroy();
  }

  private void captured(LiveShotDetector.Reading reading, BallType type, double width) {
    SimulatorConnector.publish(this, reading, type);
    String report =
        String.format(
            Locale.US,
            "%s · %s\n%.2f m/s  /  %.1f mph\n%s: %.1f°\n%d points · estimated",
            reading.mode == LiveShotDetector.Mode.PUTT ? "PUTT" : "SWING",
            type.label,
            reading.speed,
            reading.speed * 2.236936,
            reading.mode == LiveShotDetector.Mode.PUTT
                ? "Direction from horizontal"
                : "Visible launch angle",
            reading.angle,
            reading.path.size());
    result.setText(report);
    try {
      JSONObject j = new JSONObject();
      j.put("time", System.currentTimeMillis());
      j.put("mode", reading.mode.name());
      j.put("ballType", type.label);
      j.put("speed_mps", reading.speed);
      j.put("angle_deg", reading.angle);
      j.put("frame_width_m", width);
      j.put("points", reading.path.size());
      j.put("report", report);
      history.put(j);
      historyButton.setText("Readings (" + Math.min(100, history.length()) + ")");
      while (history.length() > 100) history.remove(0);
      getSharedPreferences("fairway", 0)
          .edit()
          .putString("liveReadings", history.toString())
          .apply();
    } catch (JSONException ignored) {
    }
    if (getIntent().getBooleanExtra("gameInput", false)) {
      Intent data = new Intent();
      data.putExtra("speed", reading.speed);
      data.putExtra("angle", reading.angle);
      data.putExtra("putting", reading.mode == LiveShotDetector.Mode.PUTT);
      data.putExtra("ballType", type.label);
      setResult(RESULT_OK, data);
      finish();
    }
  }

  private void showHistory() {
    StringBuilder s = new StringBuilder();
    for (int i = history.length() - 1; i >= 0; i--) {
      JSONObject j = history.optJSONObject(i);
      s.append(
              new java.text.SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())
                  .format(new Date(j.optLong("time"))))
          .append('\n')
          .append(j.optString("report"))
          .append("\n\n");
    }
    new AlertDialog.Builder(this)
        .setTitle("Live readings · " + history.length())
        .setMessage(s.length() == 0 ? "No live shots captured yet." : s.toString())
        .setPositiveButton("Close", null)
        .show();
  }

  private final class CameraRun {
    final HandlerThread thread = new HandlerThread("Fairway live camera");
    Handler handler;
    CameraDevice device;
    CameraCaptureSession session;
    ImageReader reader;
    volatile boolean stopped;
    LiveShotDetector detector;
    LiveShotDetector.Mode runMode;
    BallType runBall;
    double runWidth;
    boolean runPaused, runCalibrated;
    int rotation;
    long lastFps;
    int frames;
    double measuredFps;
    final AtomicBoolean pending = new AtomicBoolean();

    void start() {
      thread.start();
      handler = new Handler(thread.getLooper());
      configure();
      handler.post(this::open);
    }

    void configure() {
      final LiveShotDetector.Mode m = mode;
      final BallType b = ballType;
      final double width = metres, x = targetX, y = targetY;
      final boolean pause = paused;
      final boolean calibration = calibrated;
      handler.post(
          () -> {
            runMode = m;
            runBall = b;
            runWidth = width;
            runPaused = pause;
            runCalibrated = calibration;
            detector = new LiveShotDetector(m, width, x, y, b);
          });
    }

    void showError(String s) {
      runOnUiThread(
          () -> {
            if (!stopped && camera == this) status.setText(s);
          });
    }

    void open() {
      if (stopped) return;
      try {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        String chosen = null;
        CameraCharacteristics chars = null;
        for (String id : manager.getCameraIdList()) {
          CameraCharacteristics c = manager.getCameraCharacteristics(id);
          Integer facing = c.get(CameraCharacteristics.LENS_FACING);
          if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
            chosen = id;
            chars = c;
            break;
          }
        }
        if (chosen == null) {
          showError("No rear camera is available on this device.");
          return;
        }
        StreamConfigurationMap map =
            chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) throw new IllegalStateException();
        Size best = null;
        long score = Long.MAX_VALUE;
        for (Size size : map.getOutputSizes(ImageFormat.YUV_420_888)) {
          long cost = Math.abs(size.getWidth() * size.getHeight() - 640 * 480);
          if (size.getWidth() < 320 || size.getWidth() > 1920) continue;
          if (cost < score) {
            score = cost;
            best = size;
          }
        }
        if (best == null) throw new IllegalStateException();
        Integer sensor = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int display = getWindowManager().getDefaultDisplay().getRotation() * 90;
        rotation = ((sensor == null ? 90 : sensor) - display + 360) % 360;
        reader =
            ImageReader.newInstance(best.getWidth(), best.getHeight(), ImageFormat.YUV_420_888, 3);
        reader.setOnImageAvailableListener(this::frame, handler);
        Range<Integer> fps = null;
        Range<Integer>[] ranges =
            chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        long duration = map.getOutputMinFrameDuration(ImageFormat.YUV_420_888, best);
        int limit = duration > 0 ? (int) (1e9 / duration) : 60;
        if (ranges != null)
          for (Range<Integer> r : ranges)
            if (r.getUpper() <= Math.min(60, limit)
                && (fps == null
                    || r.getUpper() > fps.getUpper()
                    || (r.getUpper().equals(fps.getUpper()) && r.getLower() > fps.getLower())))
              fps = r;
        final Range<Integer> selectedFps = fps;
        final int[] af = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
          showError("Camera permission is required.");
          return;
        }
        manager.openCamera(
            chosen,
            new CameraDevice.StateCallback() {
              @Override
              public void onOpened(CameraDevice d) {
                if (stopped) {
                  d.close();
                  return;
                }
                device = d;
                try {
                  d.createCaptureSession(
                      Collections.singletonList(reader.getSurface()),
                      new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession s) {
                          if (stopped) {
                            s.close();
                            return;
                          }
                          session = s;
                          try {
                            CaptureRequest.Builder request =
                                d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            request.addTarget(reader.getSurface());
                            request.set(
                                CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                            if (selectedFps != null)
                              request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, selectedFps);
                            if (af != null)
                              for (int f : af)
                                if (f == CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) {
                                  request.set(CaptureRequest.CONTROL_AF_MODE, f);
                                  break;
                                }
                            s.setRepeatingRequest(request.build(), null, handler);
                          } catch (CameraAccessException | IllegalArgumentException e) {
                            showError("Camera stream could not start. Try Restart camera.");
                          }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession s) {
                          showError(
                              "This camera does not support the analysis stream. Try another"
                                  + " phone.");
                        }
                      },
                      handler);
                } catch (CameraAccessException | IllegalArgumentException e) {
                  showError("Could not configure the camera.");
                }
              }

              @Override
              public void onDisconnected(CameraDevice d) {
                d.close();
                showError("Camera disconnected. Tap Restart camera.");
              }

              @Override
              public void onError(CameraDevice d, int error) {
                d.close();
                showError("Camera unavailable (" + error + "). Close other camera apps and retry.");
              }
            },
            handler);
      } catch (Exception e) {
        showError("Unable to open rear camera. Tap Restart camera to retry.");
      }
    }

    void frame(ImageReader source) {
      try (Image image = source.acquireLatestImage()) {
        if (image == null || stopped) return;
        int step = Math.max(1, (image.getWidth() + 639) / 640),
            sw = image.getWidth() / step,
            sh = image.getHeight() / step;
        int w = rotation % 180 == 0 ? sw : sh, h = rotation % 180 == 0 ? sh : sw;
        byte[] gray = new byte[w * h];
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yy = planes[0].getBuffer(),
            uu = planes[1].getBuffer(),
            vv = planes[2].getBuffer();
        boolean draw = pending.compareAndSet(false, true);
        int[] rgb = draw ? new int[w * h] : null;
        for (int y = 0; y < sh; y++)
          for (int x = 0; x < sw; x++) {
            int sx = x * step,
                sy = y * step,
                l = yy.get(sy * planes[0].getRowStride() + sx * planes[0].getPixelStride()) & 255;
            int dx = x, dy = y;
            if (rotation == 90) {
              dx = sh - 1 - y;
              dy = x;
            } else if (rotation == 180) {
              dx = sw - 1 - x;
              dy = sh - 1 - y;
            } else if (rotation == 270) {
              dx = y;
              dy = sw - 1 - x;
            }
            int at = dy * w + dx;
            gray[at] = (byte) l;
            if (draw) {
              int
                  u =
                      (uu.get(
                                  (sy / 2) * planes[1].getRowStride()
                                      + (sx / 2) * planes[1].getPixelStride())
                              & 255)
                          - 128,
                  v =
                      (vv.get(
                                  (sy / 2) * planes[2].getRowStride()
                                      + (sx / 2) * planes[2].getPixelStride())
                              & 255)
                          - 128;
              int c = Math.max(0, l - 16) * 298;
              int r = clamp((c + 409 * v + 128) >> 8),
                  g = clamp((c - 100 * u - 208 * v + 128) >> 8),
                  b = clamp((c + 516 * u + 128) >> 8);
              rgb[at] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
          }
        long ns = image.getTimestamp();
        frames++;
        if (lastFps == 0) lastFps = ns;
        if (ns - lastFps >= 1_000_000_000L) {
          measuredFps = frames * 1e9 / (ns - lastFps);
          frames = 0;
          lastFps = ns;
        }
        LiveShotDetector.Reading reading =
            runPaused || !runCalibrated ? null : detector.frame(gray, w, h, ns);
        String message =
            runPaused
                ? "Detection paused — live view remains on."
                : !runCalibrated
                    ? "Calibration required — set the measured frame width to enable detection."
                    : detector.message();
        List<LiveShotDetector.Point> trail = detector.path();
        LiveShotDetector.State state = detector.state();
        final BallType type = runBall;
        final double width = runWidth, fps = measuredFps;
        if (draw) {
          Bitmap bitmap = Bitmap.createBitmap(rgb, w, h, Bitmap.Config.ARGB_8888);
          runOnUiThread(
              () -> {
                try {
                  if (stopped || camera != this) {
                    bitmap.recycle();
                    return;
                  }
                  preview.update(bitmap, trail, state);
                  status.setText(message);
                  fpsLabel.setText(
                      String.format(
                          Locale.US,
                          "LIVE · %.0f analyzed fps · %s · %s",
                          fps,
                          runPaused
                              ? "PAUSED"
                              : !runCalibrated ? "CALIBRATION REQUIRED" : state.name(),
                          fps < 25 ? "Low frame rate — prefer putting" : "Experimental estimates"));
                  if (reading != null) captured(reading, type, width);
                } finally {
                  pending.set(false);
                }
              });
        } else if (reading != null)
          runOnUiThread(
              () -> {
                if (!stopped && camera == this) captured(reading, type, width);
              });
      } catch (Exception e) {
        pending.set(false);
        showError("Frame processing interrupted. Tap Restart camera.");
      }
    }

    int clamp(int n) {
      return Math.max(0, Math.min(255, n));
    }

    void close() {
      stopped = true;
      if (handler != null)
        handler.post(
            () -> {
              if (session != null) session.close();
              if (device != null) device.close();
              if (reader != null) reader.close();
              thread.quitSafely();
            });
    }
  }

  private final class Preview extends View {
    Bitmap bitmap;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    final RectF imageRect = new RectF();
    List<LiveShotDetector.Point> trail = Collections.emptyList();
    LiveShotDetector.State state = LiveShotDetector.State.SEARCHING;

    Preview() {
      super(LiveCameraActivity.this);
      setContentDescription(
          "Live camera. Tap the stationary ball to choose the automatic shot target.");
      setBackgroundColor(Color.BLACK);
      setClickable(true);
      setFocusable(true);
    }

    void update(Bitmap b, List<LiveShotDetector.Point> p, LiveShotDetector.State s) {
      Bitmap old = bitmap;
      bitmap = b;
      trail = p;
      state = s;
      if (old != null) old.recycle();
      invalidate();
    }

    void release() {
      if (bitmap != null) {
        bitmap.recycle();
        bitmap = null;
      }
    }

    @Override
    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (bitmap == null) {
        paint.setColor(MUTED);
        paint.setTextSize(dp(16));
        canvas.drawText("Live camera", dp(18), dp(36), paint);
        return;
      }
      float scale =
          Math.min(
              getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight());
      float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
      imageRect.set(
          (getWidth() - width) / 2,
          (getHeight() - height) / 2,
          (getWidth() + width) / 2,
          (getHeight() + height) / 2);
      canvas.drawBitmap(bitmap, null, imageRect, paint);
      paint.setColor(state == LiveShotDetector.State.READY ? GREEN : 0xffffd58a);
      paint.setStrokeWidth(dp(2));
      paint.setStyle(Paint.Style.STROKE);
      if (targetX >= 0) {
        float x = imageRect.left + (float) targetX * width,
            y = imageRect.top + (float) targetY * height;
        canvas.drawCircle(x, y, dp(18), paint);
        canvas.drawLine(x - dp(26), y, x + dp(26), y, paint);
        canvas.drawLine(x, y - dp(26), x, y + dp(26), paint);
      }
      LiveShotDetector.Point prev = null;
      paint.setColor(GREEN);
      for (LiveShotDetector.Point p : trail) {
        float x = imageRect.left + (float) p.x * scale, y = imageRect.top + (float) p.y * scale;
        if (prev != null)
          canvas.drawLine(
              imageRect.left + (float) prev.x * scale,
              imageRect.top + (float) prev.y * scale,
              x,
              y,
              paint);
        canvas.drawCircle(x, y, dp(3), paint);
        prev = p;
      }
      paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
      if (event.getActionMasked() == MotionEvent.ACTION_DOWN && bitmap != null) {
        if (imageRect.contains(event.getX(), event.getY())) {
          selectTarget(
              (event.getX() - imageRect.left) / imageRect.width(),
              (event.getY() - imageRect.top) / imageRect.height());
        } else {
          Toast.makeText(
                  LiveCameraActivity.this,
                  "Tap inside the camera image, not the black border.",
                  Toast.LENGTH_SHORT)
              .show();
        }
      }
      if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }
}
