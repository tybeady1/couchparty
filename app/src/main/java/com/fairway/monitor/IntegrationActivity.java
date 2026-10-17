package com.fairway.monitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.widget.*;

/** Connection setup stays independent of the launch monitor and camera lifecycle. */
public final class IntegrationActivity extends Activity {
  private LinearLayout root;
  private Spinner target;
  private EditText host, port, hla, spin, axis;
  private CheckBox enabled, assumptions, invert;
  private TextView status;
  private SharedPreferences p;
  private final Handler handler = new Handler();
  private final Runnable refresh =
      new Runnable() {
        @Override
        public void run() {
          status.setText(p.getString("status", "Disabled — no shots have been sent."));
          handler.postDelayed(this, 1000);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    p = SimulatorConnector.prefs(this);
    ScrollView scroll = new ScrollView(this);
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(24, 24, 24, 24);
    root.setBackgroundColor(Color.rgb(16, 26, 24));
    scroll.addView(root);
    setContentView(scroll);
    label("Simulator connections", 26);
    label(
        "Send new live camera shots to a simulator on your computer. Disabled by default. Use your"
            + " trusted local Wi-Fi; these protocols are unencrypted. No camera images or saved"
            + " history are sent.",
        15);
    target = new Spinner(this);
    SimProtocol.Target[] all = SimProtocol.Target.values();
    String[] labels = new String[all.length];
    for (int i = 0; i < all.length; i++) labels[i] = all[i].label;
    target.setAdapter(
        new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, labels));
    target.setSelection(SimProtocol.Target.from(p.getString("target", "RAW")).ordinal());
    root.addView(target);
    host = field("Computer IP address / hostname", p.getString("host", ""), false);
    port = field("TCP port", Integer.toString(p.getInt("port", selected().port)), true);
    button(
        "Use selected simulator's default port",
        () -> port.setText(Integer.toString(selected().port)));
    label(
        "OpenShotGolf / HeckleGolfSim: 49152. GSPro: 921. OpenGolfSim: 3111. Custom Fairway JSON:"
            + " 2483. Simulator must be running with its listener enabled; allow the port on your"
            + " private network firewall.",
        14);
    enabled = check("Enable sending NEW live shots after Save", p.getBoolean("enabled", false));
    label(
        "Camera readings only estimate image-plane speed and one angle. Open Connect and"
            + " OpenGolfSim need additional fields. Their game mode uses the assumptions below, NOT"
            + " measured spin or full 3D ball flight. Foam/wiffle ball behavior is not converted to"
            + " a normal ball; destination sims usually model a normal golf ball.",
        15);
    assumptions =
        check("Allow game assumptions for simulator play", p.getBoolean("assumptions", false));
    hla =
        field(
            "SWINGS: assumed horizontal direction (−45 to 45 degrees)",
            p.getString("hla", "0"),
            true);
    spin = field("SWINGS: assumed total spin (0–15000 RPM)", p.getString("spin", "0"), true);
    axis = field("SWINGS: assumed spin axis (−45 to 45 degrees)", p.getString("axis", "0"), true);
    label(
        "PUTTS: overhead camera angle supplies direction; launch angle, spin and spin axis are"
            + " assumed zero. Align travel horizontally in the camera. Reverse direction below if"
            + " the simulator shows left/right backwards. Select the putter inside your simulator"
            + " yourself.",
        15);
    invert = check("Reverse putt direction sign", p.getBoolean("invert", false));
    label(
        "Custom Fairway JSON preserves unknown fields as null and needs no assumptions. Native"
            + " adapters have been checked against public protocols and a local test receiver;"
            + " actual simulator gameplay is not yet verified.",
        14);
    button("Save connection settings", () -> save(true));
    button(
        "Save + test connection (no shot)",
        () -> {
          if (save(false)) SimulatorConnector.test(this);
        });
    button(
        "Disable sending immediately",
        () -> {
          enabled.setChecked(false);
          p.edit()
              .putBoolean("enabled", false)
              .putString("status", "Disabled — sending stopped")
              .apply();
          SimulatorConnector.settingsChanged();
        });
    status = label("", 15);
    button(
        "Inspect last outgoing shot",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Wire payload / original reading")
                .setMessage(
                    p.getString("last_payload", "No outgoing shot yet")
                        + "\n\nOriginal measurement (unknowns preserved):\n"
                        + p.getString("last_raw", "None"))
                .setPositiveButton("Close", null)
                .show());
    button("Back", this::finish);
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(refresh);
    super.onPause();
  }

  private SimProtocol.Target selected() {
    return SimProtocol.Target.values()[target.getSelectedItemPosition()];
  }

  private boolean save(boolean toast) {
    try {
      String address = host.getText().toString().trim();
      int number = Integer.parseInt(port.getText().toString());
      if (address.isEmpty()
          || address.contains("://")
          || address.contains("/")
          || number < 1
          || number > 65535)
        throw new IllegalArgumentException("Enter a computer host/IP and port 1–65535.");
      new SimProtocol.Assumptions(
          assumptions.isChecked(),
          invert.isChecked(),
          Double.parseDouble(hla.getText().toString()),
          Double.parseDouble(spin.getText().toString()),
          Double.parseDouble(axis.getText().toString()));
      if (enabled.isChecked() && selected() != SimProtocol.Target.RAW && !assumptions.isChecked())
        throw new IllegalArgumentException(
            "Select Allow game assumptions, or use Custom Fairway JSON to preserve unknown"
                + " measurements.");
      SimulatorConnector.settingsChanged();
      p.edit()
          .putString("host", address)
          .putInt("port", number)
          .putString("target", selected().name())
          .putBoolean("enabled", enabled.isChecked())
          .putBoolean("assumptions", assumptions.isChecked())
          .putBoolean("invert", invert.isChecked())
          .putString("hla", hla.getText().toString())
          .putString("spin", spin.getText().toString())
          .putString("axis", axis.getText().toString())
          .putString(
              "status",
              enabled.isChecked()
                  ? "Enabled — waiting for new live shots"
                  : "Disabled — settings saved")
          .apply();
      if (toast) Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
      return true;
    } catch (IllegalArgumentException e) {
      new AlertDialog.Builder(this)
          .setTitle("Check connection settings")
          .setMessage(e.getMessage())
          .setPositiveButton("OK", null)
          .show();
      return false;
    }
  }

  private TextView label(String text, int size) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(Color.rgb(235, 242, 230));
    v.setPadding(0, 10, 0, 10);
    root.addView(v);
    return v;
  }

  private CheckBox check(String text, boolean value) {
    CheckBox v = new CheckBox(this);
    v.setText(text);
    v.setTextColor(Color.WHITE);
    v.setChecked(value);
    root.addView(v);
    return v;
  }

  private EditText field(String label, String text, boolean numeric) {
    label(label, 14);
    EditText v = new EditText(this);
    v.setSingleLine();
    v.setTextColor(Color.WHITE);
    v.setInputType(
        numeric
            ? InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED
            : InputType.TYPE_CLASS_TEXT);
    v.setText(text);
    root.addView(v);
    return v;
  }

  private void button(String text, Runnable action) {
    Button b = new Button(this);
    b.setText(text);
    b.setOnClickListener(v -> action.run());
    root.addView(b);
  }
}
