package com.fairway.monitor;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Capability-gated high-speed recording followed by bounded marked-ball analysis. */
public class SpinActivity extends Activity {
  private static final class Mode {
    String camera;
    Size size;
    int fps;

    Mode(String c, Size s, int f) {
      camera = c;
      size = s;
      fps = f;
    }

    public String toString() {
      return "Camera " + camera + " · " + size + " · " + fps + " fps";
    }
  }

  private final ArrayList<Mode> modes = new ArrayList<>();
  private Spinner choices;
  private TextView status;
  private TextureView preview;
  private Button open, record, review;
  private HandlerThread thread;
  private Handler handler;
  private CameraDevice device;
  private CameraCaptureSession session;
  private MediaRecorder recorder;
  private Surface surface;
  private volatile boolean closed = true, recording, closing;
  private int generation;
  private Mode selected;
  private File clip;
  private long startNs;

  public void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 12, 24, 12);
    setContentView(root);
    TextView title = new TextView(this);
    title.setText("Experimental marked-ball spin");
    title.setTextSize(23);
    root.addView(title);
    Button help = new Button(this);
    help.setText("Setup / limitations");
    help.setOnClickListener(v -> help());
    root.addView(help);
    choices = new Spinner(this);
    root.addView(choices);
    preview = new TextureView(this);
    root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
    status = new TextView(this);
    status.setTextSize(14);
    root.addView(status);
    LinearLayout buttons = new LinearLayout(this);
    root.addView(buttons);
    open = new Button(this);
    open.setText("Open preview");
    buttons.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
    open.setOnClickListener(v -> open());
    record = new Button(this);
    record.setText("Record 2 seconds");
    record.setEnabled(false);
    buttons.addView(record, new LinearLayout.LayoutParams(0, -2, 1));
    record.setOnClickListener(v -> record());
    review = new Button(this);
    review.setText("Review capture");
    review.setEnabled(false);
    root.addView(review);
    review.setOnClickListener(v -> review());
    Button back = new Button(this);
    back.setText("Back");
    root.addView(back);
    back.setOnClickListener(v -> finish());
    root.removeView(preview);
    LinearLayout outer = new LinearLayout(this);
    setContentView(outer);
    outer.addView(preview, new LinearLayout.LayoutParams(0, -1, 1));
    ScrollView panel = new ScrollView(this);
    panel.addView(root);
    outer.addView(
        panel,
        new LinearLayout.LayoutParams(
            (int) (300 * getResources().getDisplayMetrics().density), -1));
    discover();
  }

  private void help() {
    new AlertDialog.Builder(this)
        .setTitle("Marked-ball spin experiment")
        .setMessage(
            "Use a white ball with ONE red dot on the face toward the camera, about halfway from"
                + " its center to its edge. Use a dark background and bright, even light. The ball"
                + " must be at least 24 pixels wide in the recorded image; aim for 40–100. Mount"
                + " the phone securely outside the club and ball path.\n\n"
                + "Start with a gently spinning ball or a slow putt. For this model the rotation"
                + " axis must point toward the camera. A tilted axis, a disappearing dot, blur or"
                + " multiple red marks invalidate the estimate. It does not solve 3D spin axis or"
                + " distinguish true backspin from sidespin.\n\n"
                + "Open preview, record a 2-second burst, then review. Select the first frame of"
                + " clear rotation and tap the ball. Set a realistic maximum RPM before analyzing."
                + " The sampling ceiling is capture fps × 10 (240 fps → 2400 RPM). Faster spin can"
                + " alias into a plausible slower result; the ceiling is an assumption, not a"
                + " guarantee.\n\n"
                + "Results stay separate from launch-monitor measurements and are never sent to"
                + " simulators. OnePlus stock slow-motion modes may not be exposed through Camera2."
                + " No standard-speed fallback or interpolated slow-motion import is used.")
        .setPositiveButton("Got it", null)
        .show();
  }

  private void discover() {
    if (Build.VERSION.SDK_INT < 28) {
      status.setText("This experiment requires Android 9+ for frame-by-frame review.");
      open.setEnabled(false);
      return;
    }
    try {
      CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
      for (String id : manager.getCameraIdList()) {
        CameraCharacteristics c = manager.getCameraCharacteristics(id);
        if (!Integer.valueOf(CameraCharacteristics.LENS_FACING_BACK)
            .equals(c.get(CameraCharacteristics.LENS_FACING))) continue;
        int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        boolean high = false;
        if (caps != null)
          for (int n : caps)
            if (n
                == CameraCharacteristics
                    .REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) high = true;
        if (!high) continue;
        StreamConfigurationMap map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) continue;
        for (Size size : map.getHighSpeedVideoSizes())
          for (Range<Integer> range : map.getHighSpeedVideoFpsRangesFor(size))
            if (range.getLower().equals(range.getUpper())
                && range.getUpper() >= 120
                && range.getUpper() <= 480
                && size.getWidth() <= 1920) modes.add(new Mode(id, size, range.getUpper()));
      }
      modes.sort(
          (a, b) ->
              a.fps != b.fps
                  ? Integer.compare(b.fps, a.fps)
                  : Integer.compare(
                      a.size.getWidth() * a.size.getHeight(),
                      b.size.getWidth() * b.size.getHeight()));
      choices.setAdapter(
          new ArrayAdapter<Mode>(this, android.R.layout.simple_spinner_dropdown_item, modes));
      status.setText(
          modes.isEmpty()
              ? "No supported 120–480 fps Camera2 mode is exposed on this device. Stock camera slow"
                  + " motion does not guarantee third-party access."
              : Build.MANUFACTURER
                  + " "
                  + Build.MODEL
                  + " · "
                  + modes.size()
                  + " exposed high-speed modes. Choose a mode, then open preview.");
      open.setEnabled(!modes.isEmpty());
    } catch (Exception e) {
      status.setText("Cannot enumerate camera modes: " + e.getMessage());
      open.setEnabled(false);
    }
  }

  private void ui(Runnable r) {
    runOnUiThread(
        () -> {
          if (!isFinishing() && !isDestroyed()) r.run();
        });
  }

  private void open() {
    if (closing) {
      status.setText("Camera is closing; retry in a moment.");
      return;
    }
    if (handler != null) {
      shutdown();
      status.setText("Closing previous preview; tap Open preview again.");
      return;
    }
    if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.CAMERA}, 440);
      return;
    }
    if (!preview.isAvailable()) {
      status.setText("Wait for the preview surface, then retry.");
      return;
    }
    shutdown();
    closed = false;
    int ticket = ++generation;
    selected = modes.get(choices.getSelectedItemPosition());
    clip = new File(getCacheDir(), "spin-capture.mp4");
    review.setEnabled(false);
    open.setEnabled(false);
    choices.setEnabled(false);
    status.setText("Opening " + selected + "…");
    thread = new HandlerThread("Spin capture");
    thread.start();
    handler = new Handler(thread.getLooper());
    preview
        .getSurfaceTexture()
        .setDefaultBufferSize(selected.size.getWidth(), selected.size.getHeight());
    surface = new Surface(preview.getSurfaceTexture());
    handler.post(
        () -> {
          try {
            recorder = new MediaRecorder();
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(clip.getAbsolutePath());
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(selected.size.getWidth(), selected.size.getHeight());
            recorder.setVideoFrameRate(selected.fps);
            recorder.setVideoEncodingBitRate(
                Math.min(
                    80000000,
                    selected.size.getWidth() * selected.size.getHeight() * selected.fps / 12));
            recorder.prepare();
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            manager.openCamera(
                selected.camera,
                new CameraDevice.StateCallback() {
                  public void onOpened(CameraDevice d) {
                    if (closed || ticket != generation) {
                      d.close();
                      return;
                    }
                    device = d;
                    try {
                      d.createConstrainedHighSpeedCaptureSession(
                          Arrays.asList(surface, recorder.getSurface()),
                          new CameraCaptureSession.StateCallback() {
                            public void onConfigured(CameraCaptureSession s) {
                              if (closed || ticket != generation) {
                                s.close();
                                return;
                              }
                              session = s;
                              try {
                                repeat(false);
                                ui(
                                    () -> {
                                      record.setEnabled(true);
                                      status.setText(
                                          "High-speed preview ready. Frame the marked ball, then"
                                              + " record. Preview may look stretched; analysis uses"
                                              + " original pixels.");
                                    });
                              } catch (Exception e) {
                                fail(e);
                              }
                            }

                            public void onConfigureFailed(CameraCaptureSession s) {
                              if (closed || ticket != generation) return;
                              fail(
                                  new IOException(
                                      "High-speed session rejected. Try another listed mode."));
                            }
                          },
                          handler);
                    } catch (Exception e) {
                      fail(e);
                    }
                  }

                  public void onDisconnected(CameraDevice d) {
                    d.close();
                    if (closed || ticket != generation) return;
                    fail(new IOException("Camera disconnected"));
                  }

                  public void onError(CameraDevice d, int e) {
                    d.close();
                    if (closed || ticket != generation) return;
                    fail(new IOException("Camera error " + e));
                  }
                },
                handler);
          } catch (Exception e) {
            fail(e);
          }
        });
  }

  private void repeat(boolean capture) throws Exception {
    CaptureRequest.Builder b =
        device.createCaptureRequest(
            capture ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);
    b.addTarget(surface);
    if (capture) b.addTarget(recorder.getSurface());
    b.set(
        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(selected.fps, selected.fps));
    session.setRepeatingBurst(
        ((CameraConstrainedHighSpeedCaptureSession) session).createHighSpeedRequestList(b.build()),
        null,
        handler);
  }

  private void fail(Exception e) {
    ui(
        () -> {
          status.setText("Capture unavailable: " + e.getMessage());
          shutdown();
          open.setEnabled(!modes.isEmpty());
          choices.setEnabled(true);
          record.setEnabled(false);
        });
  }

  private void record() {
    record.setEnabled(false);
    status.setText("Recording 2 seconds — perform the marked-ball motion now.");
    handler.post(
        () -> {
          try {
            repeat(true);
            recorder.start();
            recording = true;
            startNs = System.nanoTime();
            handler.postDelayed(() -> stopCapture(), 2000);
          } catch (Exception e) {
            fail(e);
          }
        });
  }

  private void stopCapture() {
    if (closed || !recording) return;
    try {
      session.stopRepeating();
      session.abortCaptures();
      recorder.stop();
      recording = false;
      ui(
          () -> {
            shutdown();
            open.setEnabled(true);
            choices.setEnabled(true);
            review.setEnabled(true);
            status.setText(
                "Captured "
                    + selected.fps
                    + " fps request. Review uses encoded frame timing and rejects mismatched"
                    + " timing. Tap Review capture.");
          });
    } catch (Exception e) {
      recording = false;
      fail(e);
    }
  }

  private void review() {
    Intent i = new Intent(this, SpinReviewActivity.class);
    i.putExtra("fps", selected.fps);
    i.putExtra("file", clip.getAbsolutePath());
    startActivity(i);
  }

  private void shutdown() {
    closed = true;
    generation++;
    Handler h = handler;
    if (h != null) {
      closing = true;
      CameraCaptureSession s = session;
      CameraDevice d = device;
      MediaRecorder r = recorder;
      Surface p = surface;
      HandlerThread t = thread;
      session = null;
      device = null;
      recorder = null;
      surface = null;
      handler = null;
      h.removeCallbacksAndMessages(null);
      h.post(
          () -> {
            try {
              if (s != null) s.close();
              if (d != null) d.close();
              if (r != null) {
                if (recording)
                  try {
                    r.stop();
                  } catch (Exception ignored) {
                  }
                r.release();
              }
              if (p != null) p.release();
            } finally {
              recording = false;
              closing = false;
              t.quitSafely();
            }
          });
    }
  }

  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    if (r == 440) {
      if (g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) open();
      else status.setText("Camera permission is required.");
    }
  }

  protected void onPause() {
    shutdown();
    record.setEnabled(false);
    open.setEnabled(!modes.isEmpty());
    choices.setEnabled(true);
    super.onPause();
  }
}
