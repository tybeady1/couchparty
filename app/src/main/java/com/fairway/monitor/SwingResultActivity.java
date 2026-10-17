package com.fairway.monitor;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.widget.*;
import java.util.Locale;
import org.json.*;

public class SwingResultActivity extends Activity {
  private double speed, angle;
  private BallType ball;
  private String id;
  private LinearLayout body;

  public static Intent intent(Context c, String id, double speed, double angle, BallType ball) {
    return new Intent(c, SwingResultActivity.class)
        .putExtra("id", id)
        .putExtra("speed", speed)
        .putExtra("angle", angle)
        .putExtra("ball", ball.label);
  }

  public void onCreate(Bundle b) {
    super.onCreate(b);
    speed = getIntent().getDoubleExtra("speed", Double.NaN);
    angle = getIntent().getDoubleExtra("angle", Double.NaN);
    id = getIntent().getStringExtra("id");
    ball = BallType.from(getIntent().getStringExtra("ball"));
    if (id == null || !Double.isFinite(speed) || !Double.isFinite(angle)) {
      finish();
      return;
    }
    render();
  }

  private void text(String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(0xfff2f5eb);
    t.setPadding(0, 16, 0, 16);
    body.addView(t);
  }

  private void button(String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setOnClickListener(v -> r.run());
    body.addView(b);
  }

  private void render() {
    ScrollView scroll = new ScrollView(this);
    scroll.setBackgroundColor(0xff101a18);
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(36, 24, 36, 24);
    scroll.addView(body);
    setContentView(scroll);
    text("Your swing · " + ball.label, 26);
    button("Return to camera / history", this::finish);
    text(
        String.format(
            Locale.US,
            "Original camera reading\n%.2f m/s · %.1f mph\nVisible launch %.1f°",
            speed,
            speed * 2.236936,
            angle),
        21);
    double[] corrected = SwingProfiles.correct(this, ball, speed, angle);
    text(
        corrected[2] > 0
            ? String.format(
                Locale.US,
                "Calibrated estimate\n%.2f m/s · %.1f mph\nLaunch %.1f°\n%d paired reference shots",
                corrected[0],
                corrected[0] * 2.236936,
                corrected[1],
                (int) corrected[2])
            : corrected[2] < 0
                ? "Calibration could not be applied safely to this reading; original values are"
                      + " used."
                : "No active swing correction. Original readings are used.",
        21);
    text(
        "Pair this exact shot with BALL SPEED and vertical launch angle from your reference launch"
            + " monitor. Use the same ball, camera position, frame-width calibration and hitting"
            + " direction. Add at least 3 shots across your usual speed range. This profile is"
            + " shared by clubs for this ball type; reset it when the setup changes.",
        16);
    button("Enter / edit reference readings for this shot", this::calibrate);
    JSONArray samples = SwingProfiles.samples(this, ball);
    if (samples.length() > 0) {
      boolean active = SwingProfiles.enabled(this, ball);
      button(
          active ? "Disable swing correction" : "Enable swing correction",
          () -> {
            getSharedPreferences("fairway", 0)
                .edit()
                .putBoolean(SwingProfiles.key(ball) + "Enabled", !active)
                .apply();
            render();
          });
      button(
          "Review reference pairs",
          () -> {
            StringBuilder s = new StringBuilder();
            for (int n = 0; n < samples.length(); n++) {
              JSONObject j = samples.optJSONObject(n);
              s.append(
                  String.format(
                      Locale.US,
                      "%d. Camera %.2f m/s / %.1f° → Reference %.2f m/s / %.1f°%s\n\n",
                      n + 1,
                      j.optDouble("rawSpeed"),
                      j.optDouble("rawAngle"),
                      j.optDouble("referenceSpeed"),
                      j.optDouble("referenceAngle"),
                      id.equals(j.optString("id")) ? " (this shot)" : ""));
            }
            new AlertDialog.Builder(this)
                .setTitle("Reference pairs · " + ball.label)
                .setMessage(s)
                .setPositiveButton("Close", null)
                .show();
          });
      button(
          "Reset swing calibration",
          () ->
              new AlertDialog.Builder(this)
                  .setTitle("Reset for " + ball.label + "?")
                  .setMessage(
                      "Remove all reference pairs for this ball type. Original shot history stays"
                          + " saved.")
                  .setNegativeButton("Cancel", null)
                  .setPositiveButton(
                      "Reset",
                      (d, w) -> {
                        getSharedPreferences("fairway", 0)
                            .edit()
                            .remove(SwingProfiles.key(ball))
                            .apply();
                        render();
                      })
                  .show());
    }
    text(
        "Corrected speed and launch are estimates applied to future live swings, game input and"
            + " native simulator output. Raw JSON and original history stay unchanged. A saved shot"
            + " is never resent. Spin, club speed, carry and sideways direction are not learned"
            + " from these two measurements. A correction shown for a calibration shot is not an"
            + " independent accuracy check. Estimates outside your sampled speeds need validation.",
        15);
  }

  private void calibrate() {
    LinearLayout form = new LinearLayout(this);
    form.setOrientation(LinearLayout.VERTICAL);
    form.setPadding(32, 12, 32, 8);
    EditText rs = new EditText(this);
    rs.setHint("Reference BALL speed");
    rs.setInputType(8194);
    form.addView(rs);
    Spinner units = new Spinner(this);
    units.setAdapter(
        new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, new String[] {"mph", "m/s"}));
    form.addView(units);
    EditText ra = new EditText(this);
    ra.setHint("Reference vertical launch angle (degrees)");
    ra.setInputType(8194);
    form.addView(ra);
    JSONArray a = SwingProfiles.samples(this, ball);
    for (int n = 0; n < a.length(); n++) {
      JSONObject j = a.optJSONObject(n);
      if (id.equals(j.optString("id"))) {
        rs.setText(String.format(Locale.US, "%.3f", j.optDouble("referenceSpeed") * 2.236936));
        ra.setText(String.valueOf(j.optDouble("referenceAngle")));
      }
    }
    CheckBox same = new CheckBox(this);
    same.setText("These reference readings are from this exact shot");
    form.addView(same);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Pair reference launch monitor")
            .setView(form)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save and apply", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      if (!same.isChecked()) {
                        same.setError("Pair the same shot");
                        return;
                      }
                      try {
                        double
                            s =
                                Double.parseDouble(rs.getText().toString().trim().replace(',', '.'))
                                    / (units.getSelectedItemPosition() == 0 ? 2.236936 : 1),
                            a2 =
                                Double.parseDouble(
                                    ra.getText().toString().trim().replace(',', '.'));
                        SwingProfiles.save(this, ball, id, speed, angle, s, a2);
                        dialog.dismiss();
                        render();
                      } catch (Exception e) {
                        rs.setError(
                            e instanceof NumberFormatException
                                ? "Enter both reference readings"
                                : e.getMessage());
                      }
                    }));
    dialog.show();
  }
}
