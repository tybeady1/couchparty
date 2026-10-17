package com.fairway.monitor;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.*;

public class MainActivity extends Activity {
  private static final int BG = Color.rgb(16, 26, 24),
      CARD = Color.rgb(27, 40, 35),
      GREEN = Color.rgb(203, 240, 120),
      TEXT = Color.rgb(242, 245, 235),
      MUTED = Color.rgb(157, 174, 160);
  private final String[] clubs = {
    "Driver", "3 Wood", "5 Wood", "4 Iron", "5 Iron", "6 Iron", "7 Iron", "8 Iron", "9 Iron", "PW",
    "SW"
  };
  private final ArrayList<Shot> shots = new ArrayList<>();
  private JSONArray archives = new JSONArray();
  private String club = "7 Iron", tab = "Practice";
  private boolean metric = false, demo = true;
  private long started = System.currentTimeMillis();
  private LinearLayout root, content;
  private String exportData;
  private final Random random = new Random();

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    load();
    if (b != null) exportData = b.getString("export");
    render();
  }

  @Override
  public void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    b.putString("export", exportData);
  }

  private int dp(float n) {
    return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
  }

  private String n(double v) {
    return String.format(Locale.US, "%.0f", v);
  }

  private String unit() {
    return metric ? "m" : "yd";
  }

  private String dist(double v) {
    return n(Shot.distance(v, metric));
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private TextView text(String value, int size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("tnum");
    return t;
  }

  private TextView label(String value) {
    TextView t = text(value, 11, MUTED);
    t.setLetterSpacing(.13f);
    return t;
  }

  private LinearLayout col() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private void gap(LinearLayout l, int h) {
    Space s = new Space(this);
    l.addView(s, new LinearLayout.LayoutParams(1, dp(h)));
  }

  private void weighted(LinearLayout l, View v) {
    l.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
  }

  private TextView button(String title, boolean primary, Runnable click) {
    TextView b = text(title, 14, primary ? BG : TEXT);
    b.setTypeface(null, Typeface.BOLD);
    b.setGravity(Gravity.CENTER);
    b.setMinHeight(dp(49));
    b.setPadding(dp(12), dp(10), dp(12), dp(10));
    b.setBackground(shape(primary ? GREEN : CARD, 14));
    b.setOnClickListener(v -> click.run());
    return b;
  }

  private LinearLayout card() {
    LinearLayout l = col();
    l.setPadding(dp(18), dp(18), dp(18), dp(18));
    l.setBackground(shape(CARD, 20));
    content.addView(l, new LinearLayout.LayoutParams(-1, -2));
    return l;
  }

  private void render() {
    root = col();
    root.setBackgroundColor(BG);
    setContentView(root);
    LinearLayout header = row();
    header.setPadding(dp(22), dp(18), dp(22), dp(16));
    TextView logo = text("fairway", 29, TEXT);
    logo.setTypeface(null, Typeface.BOLD);
    weighted(header, logo);
    TextView badge = text("●  " + (demo ? "DEMO MODE" : "MANUAL MODE"), 10, GREEN);
    badge.setLetterSpacing(.1f);
    header.addView(badge);
    root.addView(header);
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    content = col();
    content.setPadding(dp(22), dp(4), dp(22), dp(22));
    scroll.addView(content);
    if (tab.equals("Practice")) practice();
    else if (tab.equals("Sessions")) sessions();
    else settings();
    LinearLayout nav = row();
    nav.setPadding(dp(12), dp(9), dp(12), dp(12));
    for (String name : new String[] {"Practice", "Sessions", "Settings"}) {
      TextView v =
          button(
              name,
              false,
              () -> {
                tab = name;
                render();
              });
      v.setTextColor(tab.equals(name) ? GREEN : MUTED);
      v.setBackground(shape(tab.equals(name) ? CARD : BG, 14));
      weighted(nav, v);
    }
    root.addView(nav);
  }

  private void practice() {
    content.addView(label("YOUR RANGE. YOUR PROGRESS."));
    gap(content, 8);
    TextView title = text("Find your next yard.", 26, TEXT);
    title.setTypeface(null, Typeface.BOLD);
    content.addView(title);
    gap(content, 6);
    content.addView(text("Practice insights, one swing at a time.", 13, MUTED));
    gap(content, 16);
    content.addView(
        button(
            "Live launch monitor  ·  Camera  ›",
            true,
            () -> startActivity(new Intent(this, LiveCameraActivity.class))));
    gap(content, 10);
    content.addView(
        button(
            "Fairway Play  ·  3D range & mini-golf  ›",
            false,
            () -> startActivity(new Intent(this, GameActivity.class))));
    gap(content, 16);
    LinearLayout controls = row();
    weighted(
        controls,
        button(
            club + "  ▾",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Choose your club")
                    .setItems(
                        clubs,
                        (d, i) -> {
                          club = clubs[i];
                          save();
                          render();
                        })
                    .show()));
    TextView session = text(String.format(Locale.US, "%02d SHOTS", shots.size()), 12, MUTED);
    session.setGravity(Gravity.END);
    weighted(controls, session);
    content.addView(controls);
    gap(content, 13);
    Shot latest = shots.isEmpty() ? null : shots.get(shots.size() - 1);
    LinearLayout hero = card();
    LinearLayout top = row();
    weighted(top, label("CARRY DISTANCE"));
    top.addView(text(latest == null ? "READY" : latest.source.toUpperCase(Locale.US), 10, GREEN));
    hero.addView(top);
    LinearLayout number = row();
    TextView big = text(latest == null ? "—" : dist(latest.carry), 68, GREEN);
    big.setTypeface(null, Typeface.BOLD);
    number.addView(big);
    TextView u = text("  " + unit(), 19, MUTED);
    number.addView(u);
    hero.addView(number);
    hero.addView(
        text(
            latest == null
                ? "Your first shot starts here."
                : latest.club
                    + "  ·  "
                    + (latest.offline == 0
                        ? "On target"
                        : dist(Math.abs(latest.offline))
                            + " "
                            + unit()
                            + (latest.offline < 0 ? " left" : " right")),
            13,
            MUTED));
    gap(content, 12);
    LinearLayout metrics = row();
    metric(
        metrics,
        "BALL SPEED",
        latest == null ? "—" : n(Shot.speed(latest.speed, metric)),
        metric ? "km/h" : "mph");
    metric(
        metrics,
        "LAUNCH",
        latest == null ? "—" : String.format(Locale.US, "%.1f", latest.launch),
        "deg");
    metric(metrics, "BACKSPIN", latest == null ? "—" : n(latest.spin), "rpm");
    content.addView(metrics);
    gap(content, 14);
    LinearLayout plot = card();
    LinearLayout plotHeader = row();
    weighted(plotHeader, text("Shot dispersion", 16, TEXT));
    plotHeader.addView(text("TOP VIEW", 10, MUTED));
    plot.addView(plotHeader);
    gap(plot, 8);
    Plot chart = new Plot(new ArrayList<>(shots));
    plot.addView(chart, new LinearLayout.LayoutParams(-1, dp(185)));
    plot.addView(text("Landing positions · " + unit() + " · all clubs", 11, MUTED));
    gap(content, 14);
    content.addView(
        button(
            demo ? "＋  Simulate shot" : "＋  Add measured shot",
            true,
            () -> {
              if (demo) simulate();
              else manual();
            }));
    gap(content, 9);
    if (demo) {
      content.addView(button("Enter a measured shot", false, this::manual));
      gap(content, 14);
    }
    if (!shots.isEmpty()) {
      LinearLayout summary = row();
      weighted(summary, text("SESSION AVERAGE", 10, MUTED));
      double avg = 0, best = 0;
      for (Shot s : shots) {
        avg += s.carry;
        best = Math.max(best, s.carry);
      }
      summary.addView(
          text(dist(avg / shots.size()) + " " + unit() + "  ·  Best " + dist(best), 12, GREEN));
      content.addView(summary);
      gap(content, 17);
      content.addView(text("Recent shots", 18, TEXT));
      gap(content, 8);
      for (int i = shots.size() - 1; i >= Math.max(0, shots.size() - 5); i--) {
        final int index = i;
        Shot s = shots.get(i);
        LinearLayout r = row();
        r.setPadding(0, dp(10), 0, dp(10));
        weighted(
            r,
            text(
                String.format(Locale.US, "%02d", i + 1) + "   " + s.club + " · " + s.source,
                12,
                MUTED));
        r.addView(text(dist(s.carry) + " " + unit() + "  ›", 15, TEXT));
        r.setOnClickListener(v -> shotDetail(index));
        content.addView(r);
      }
      gap(content, 12);
      content.addView(button("Finish & save session", false, this::finishSession));
    }
  }

  private void metric(LinearLayout r, String title, String value, String units) {
    LinearLayout c = col();
    c.setPadding(dp(10), dp(15), dp(6), dp(15));
    c.setBackground(shape(CARD, 14));
    c.addView(text(title, 9, MUTED));
    gap(c, 7);
    c.addView(text(value, 24, TEXT));
    c.addView(text(units, 11, MUTED));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(0, 0, dp(4), 0);
    r.addView(c, p);
  }

  private void simulate() {
    int index = Arrays.asList(clubs).indexOf(club);
    double base = new double[] {255, 225, 205, 185, 175, 165, 155, 145, 132, 115, 80}[index];
    shots.add(
        new Shot(
            club,
            "demo",
            base + random.nextGaussian() * 8,
            base * .51 + random.nextDouble() * 5,
            index < 3 ? 12 + random.nextDouble() * 4 : 18 + index * .8 + random.nextDouble() * 3,
            index < 3 ? 2200 + random.nextInt(1000) : 4200 + index * 380 + random.nextInt(600),
            random.nextGaussian() * 9,
            System.currentTimeMillis(),
            selectedBallType()));
    save();
    render();
  }

  private void manual() {
    LinearLayout form = col();
    form.setPadding(dp(24), dp(8), dp(24), dp(8));
    form.addView(
        text(
            "Enter measured values for " + club + ".\nUse yards and mph for this form.",
            13,
            MUTED));
    String[] hints = {
      "Carry · 1–450 yd",
      "Ball speed · 1–250 mph",
      "Launch · 0–70°",
      "Backspin · 0–15,000 rpm",
      "Offline · −150 to 150 yd (left is −)"
    };
    EditText[] fields = new EditText[5];
    for (int i = 0; i < 5; i++) {
      fields[i] = new EditText(this);
      fields[i].setTextSize(14);
      fields[i].setHint(hints[i]);
      fields[i].setSingleLine();
      fields[i].setInputType(
          InputType.TYPE_CLASS_NUMBER
              | InputType.TYPE_NUMBER_FLAG_DECIMAL
              | InputType.TYPE_NUMBER_FLAG_SIGNED);
      form.addView(fields[i], new LinearLayout.LayoutParams(-1, dp(54)));
    }
    ScrollView sc = new ScrollView(this);
    sc.addView(form);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Add measured shot")
            .setView(sc)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save shot", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(
                    w -> {
                      try {
                        double[] a = new double[5];
                        for (int i = 0; i < 5; i++)
                          a[i] = Double.parseDouble(fields[i].getText().toString().trim());
                        shots.add(
                            new Shot(
                                club,
                                "manual",
                                a[0],
                                a[1],
                                a[2],
                                a[3],
                                a[4],
                                System.currentTimeMillis(),
                                selectedBallType()));
                        save();
                        dialog.dismiss();
                        render();
                      } catch (IllegalArgumentException e) {
                        Toast.makeText(
                                this,
                                "Fill every field with a number in its displayed range.",
                                Toast.LENGTH_LONG)
                            .show();
                      }
                    }));
    dialog.show();
  }

  private void shotDetail(int index) {
    Shot s = shots.get(index);
    new AlertDialog.Builder(this)
        .setTitle("Shot " + (index + 1) + " · " + s.club)
        .setMessage(
            s.source.toUpperCase(Locale.US)
                + " · "
                + s.ballType
                + "\nCarry: "
                + dist(s.carry)
                + " "
                + unit()
                + "\nBall speed: "
                + n(Shot.speed(s.speed, metric))
                + (metric ? " km/h" : " mph")
                + "\nLaunch: "
                + s.launch
                + "°\nSpin: "
                + n(s.spin)
                + " rpm")
        .setPositiveButton("Done", null)
        .setNeutralButton(
            "Delete shot",
            (d, w) -> {
              shots.remove(index);
              save();
              render();
            })
        .show();
  }

  private void finishSession() {
    if (shots.isEmpty()) return;
    try {
      JSONObject s = new JSONObject();
      s.put("started", started);
      s.put("shots", encode(shots));
      archives.put(s);
      shots.clear();
      started = System.currentTimeMillis();
      save();
      tab = "Sessions";
      render();
    } catch (JSONException e) {
      toast("Unable to save session.");
    }
  }

  private void sessions() {
    content.addView(label("THE BIGGER PICTURE"));
    gap(content, 8);
    content.addView(text("Your sessions", 28, TEXT));
    gap(content, 8);
    content.addView(text("Every swing tells a story.", 14, MUTED));
    gap(content, 22);
    if (archives.length() == 0) {
      LinearLayout c = card();
      c.addView(text("A fresh scorecard.", 21, TEXT));
      gap(c, 8);
      c.addView(
          text(
              "Finish a practice session to save your shots here. Your active session is saved"
                  + " automatically on this device.",
              14,
              MUTED));
      gap(c, 20);
      c.addView(
          button(
              "Go to practice",
              true,
              () -> {
                tab = "Practice";
                render();
              }));
    }
    for (int i = archives.length() - 1; i >= 0; i--) {
      final int index = i;
      JSONObject s = archives.optJSONObject(i);
      ArrayList<Shot> list = decode(s.optJSONArray("shots"));
      LinearLayout c = card();
      c.addView(text(date(s.optLong("started")), 18, TEXT));
      gap(c, 7);
      double avg = 0;
      for (Shot shot : list) avg += shot.carry;
      c.addView(
          text(
              list.size()
                  + " shots  ·  "
                  + (list.isEmpty() ? "0" : dist(avg / list.size()))
                  + " "
                  + unit()
                  + " average carry",
              13,
              MUTED));
      gap(c, 8);
      int demos = 0;
      for (Shot shot : list) if (shot.source.equals("demo")) demos++;
      c.addView(text(demos + " simulated · " + (list.size() - demos) + " manual", 11, GREEN));
      gap(c, 14);
      c.addView(button("View session", false, () -> viewSession(index)));
      gap(content, 12);
    }
  }

  private String date(long t) {
    return new SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(new Date(t));
  }

  private void viewSession(int index) {
    JSONObject s = archives.optJSONObject(index);
    ArrayList<Shot> list = decode(s.optJSONArray("shots"));
    StringBuilder body = new StringBuilder();
    int i = 0;
    for (Shot shot : list)
      body.append(++i)
          .append(".  ")
          .append(shot.club)
          .append(" · ")
          .append(dist(shot.carry))
          .append(" ")
          .append(unit())
          .append(" · ")
          .append(shot.source)
          .append("\n");
    new AlertDialog.Builder(this)
        .setTitle(date(s.optLong("started")))
        .setMessage(body.toString())
        .setPositiveButton("Export CSV", (d, w) -> export(list))
        .setNegativeButton("Close", null)
        .setNeutralButton(
            "Delete",
            (d, w) ->
                new AlertDialog.Builder(this)
                    .setTitle("Delete saved session?")
                    .setMessage("This removes the session and its shots from this device.")
                    .setNegativeButton("Keep", null)
                    .setPositiveButton(
                        "Delete",
                        (a, b) -> {
                          archives.remove(index);
                          save();
                          render();
                        })
                    .show())
        .show();
  }

  private void settings() {
    content.addView(label("MAKE IT YOURS"));
    gap(content, 8);
    content.addView(text("Range settings", 28, TEXT));
    gap(content, 23);
    content.addView(
        button(
            "Connect to a golf simulator",
            false,
            () -> startActivity(new Intent(this, IntegrationActivity.class))));
    gap(content, 15);
    content.addView(
        button(
            "App version & updates",
            false,
            () -> startActivity(new Intent(this, UpdateActivity.class))));
    gap(content, 15);
    content.addView(
        button(
            "Experimental marked-ball spin",
            false,
            () -> startActivity(new Intent(this, SpinActivity.class))));
    gap(content, 15);
    LinearLayout balls = card();
    balls.addView(text("Ball type", 18, TEXT));
    gap(balls, 10);
    balls.addView(
        button(
            selectedBallType() + " ▾",
            false,
            () ->
                new AlertDialog.Builder(this)
                    .setTitle("Ball type")
                    .setItems(
                        BallType.labels(),
                        (d, i) -> {
                          getSharedPreferences("fairway", 0)
                              .edit()
                              .putString("ballType", BallType.values()[i].label)
                              .apply();
                          render();
                        })
                    .show()));
    gap(balls, 10);
    balls.addView(
        text(
            "Applies to live detection and new practice shots. Foam and wiffle readings are not"
                + " converted to normal-ball performance. Demo values are illustrative only.",
            13,
            MUTED));
    gap(content, 15);
    LinearLayout c = card();
    c.addView(text("Shot source", 18, TEXT));
    gap(c, 10);
    c.addView(
        text(
            "Demo generates simulated readings. Manual entry records values from your own measuring"
                + " equipment.",
            14,
            MUTED));
    gap(c, 15);
    c.addView(
        button(
            demo ? "Demo simulator  ✓" : "Manual entry  ✓",
            true,
            () -> {
              demo = !demo;
              save();
              render();
            }));
    gap(content, 15);
    LinearLayout units = card();
    units.addView(text("Display units", 18, TEXT));
    gap(units, 12);
    units.addView(
        button(
            metric ? "Metric · metres / km/h" : "Imperial · yards / mph",
            false,
            () -> {
              metric = !metric;
              save();
              render();
            }));
    gap(content, 15);
    LinearLayout data = card();
    data.addView(text("Your data stays with you", 18, TEXT));
    gap(data, 10);
    data.addView(
        text(
            "Sessions are stored locally. CSV exports use yards, mph, degrees and rpm, with a"
                + " source label for every shot.",
            14,
            MUTED));
    gap(data, 16);
    data.addView(
        button(
            "Export active session",
            false,
            () -> {
              if (shots.isEmpty()) toast("Add a shot first.");
              else export(new ArrayList<>(shots));
            }));
    gap(content, 18);
    content.addView(text("ABOUT FAIRWAY", 11, GREEN));
    gap(content, 8);
    content.addView(
        text(
            "Version 1.0\n"
                + "Live camera automatically detects ball movement for swings and indoor putts."
                + " Estimates are experimental. Carry and spin are unavailable"
                + " from the camera. Practice dispersion uses manual or simulated landing"
                + " positions. No radar connection is included.",
            13,
            MUTED));
  }

  private String selectedBallType() {
    return BallType.from(getSharedPreferences("fairway", 0).getString("ballType", "Normal ball"))
        .label;
  }

  private void export(ArrayList<Shot> list) {
    StringBuilder csv =
        new StringBuilder(
            "timestamp_ms,club,source,carry_yd,ball_speed_mph,launch_deg,backspin_rpm,offline_yd,ball_type\n");
    for (Shot s : list) csv.append(s.csv()).append('\n');
    exportData = csv.toString();
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("text/csv");
    intent.putExtra(Intent.EXTRA_TITLE, "fairway-" + System.currentTimeMillis() + ".csv");
    startActivityForResult(intent, 42);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == 42
        && result == RESULT_OK
        && data != null
        && data.getData() != null
        && exportData != null)
      try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
        out.write(exportData.getBytes(StandardCharsets.UTF_8));
        toast("CSV exported.");
      } catch (Exception e) {
        toast("Couldn't write this file. Try another location.");
      }
  }

  private void toast(String m) {
    Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
  }

  private JSONArray encode(List<Shot> list) throws JSONException {
    JSONArray a = new JSONArray();
    for (Shot s : list) {
      JSONObject j = new JSONObject();
      j.put("club", s.club);
      j.put("source", s.source);
      j.put("ballType", s.ballType);
      j.put("carry", s.carry);
      j.put("speed", s.speed);
      j.put("launch", s.launch);
      j.put("spin", s.spin);
      j.put("offline", s.offline);
      j.put("time", s.time);
      a.put(j);
    }
    return a;
  }

  private ArrayList<Shot> decode(JSONArray a) {
    ArrayList<Shot> list = new ArrayList<>();
    if (a != null)
      for (int i = 0; i < a.length(); i++)
        try {
          JSONObject j = a.getJSONObject(i);
          list.add(
              new Shot(
                  j.getString("club"),
                  j.getString("source"),
                  j.getDouble("carry"),
                  j.getDouble("speed"),
                  j.getDouble("launch"),
                  j.getDouble("spin"),
                  j.getDouble("offline"),
                  j.getLong("time"),
                  j.optString("ballType", "Normal ball")));
        } catch (Exception ignored) {
        }
    return list;
  }

  private void save() {
    try {
      getSharedPreferences("fairway", 0)
          .edit()
          .putString("shots", encode(shots).toString())
          .putString("archives", archives.toString())
          .putString("club", club)
          .putBoolean("metric", metric)
          .putBoolean("demo", demo)
          .putLong("started", started)
          .apply();
    } catch (JSONException e) {
      toast("Unable to save data.");
    }
  }

  private void load() {
    android.content.SharedPreferences p = getSharedPreferences("fairway", 0);
    club = p.getString("club", "7 Iron");
    metric = p.getBoolean("metric", false);
    demo = p.getBoolean("demo", true);
    started = p.getLong("started", started);
    try {
      shots.addAll(decode(new JSONArray(p.getString("shots", "[]"))));
      archives = new JSONArray(p.getString("archives", "[]"));
    } catch (JSONException e) {
      toast("Saved data could not be loaded.");
    }
  }

  private class Plot extends View {
    private final Paint paint = new Paint(3);
    private final ArrayList<Shot> data;

    Plot(ArrayList<Shot> list) {
      super(MainActivity.this);
      data = list;
      setContentDescription(
          "Shot dispersion chart. "
              + list.size()
              + " landing positions. Left and right offset from the target line.");
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      float w = getWidth(),
          h = getHeight(),
          left = dp(27),
          right = w - dp(12),
          top = dp(12),
          bottom = h - dp(23),
          center = (left + right) / 2;
      double max = 200, side = 30;
      for (Shot s : data) {
        max = Math.max(max, Math.ceil(s.carry / 50) * 50);
        side = Math.max(side, Math.ceil(Math.abs(s.offline) / 10) * 10);
      }
      paint.setColor(Color.rgb(34, 53, 41));
      c.drawRoundRect(
          center - (right - left) * .21f,
          top,
          center + (right - left) * .21f,
          bottom,
          dp(24),
          dp(24),
          paint);
      paint.setStrokeWidth(dp(1));
      paint.setTextSize(dp(9));
      for (int i = 0; i < 5; i++) {
        float y = bottom - (bottom - top) * i / 4;
        paint.setColor(Color.rgb(52, 70, 58));
        c.drawLine(left, y, right, y, paint);
        paint.setColor(MUTED);
        c.drawText(dist(max * i / 4), 0, y + dp(3), paint);
      }
      paint.setColor(Color.rgb(105, 130, 92));
      paint.setPathEffect(new DashPathEffect(new float[] {dp(4), dp(5)}, 0));
      c.drawLine(center, top, center, bottom, paint);
      paint.setPathEffect(null);
      paint.setColor(MUTED);
      c.drawText(dist(side) + " L", left, bottom + dp(17), paint);
      c.drawText("TARGET", center - dp(17), bottom + dp(17), paint);
      c.drawText(dist(side) + " R", right - dp(23), bottom + dp(17), paint);
      for (int i = 0; i < data.size(); i++) {
        Shot s = data.get(i);
        float x = center + (float) (s.offline / side) * (right - left) / 2,
            y = bottom - (float) (s.carry / max) * (bottom - top);
        paint.setColor(i == data.size() - 1 ? GREEN : Color.rgb(112, 155, 105));
        c.drawCircle(x, y, dp(i == data.size() - 1 ? 5 : 3), paint);
      }
      if (data.isEmpty()) {
        paint.setColor(TEXT);
        paint.setTextSize(dp(13));
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText("Your landing pattern appears here", center, top + (bottom - top) * .48f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
      }
    }
  }
}
