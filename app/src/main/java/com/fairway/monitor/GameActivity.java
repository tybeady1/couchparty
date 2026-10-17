package com.fairway.monitor;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.opengl.GLSurfaceView;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Playable touch games with optional one-shot input from the live camera monitor. */
public class GameActivity extends Activity {
  private static final int GREEN = 0xffcbf078, TEXT = 0xfff2f5eb, MUTED = 0xffb4c5b8;
  private GolfGame game;
  private GolfRenderer renderer;
  private GLSurfaceView surface;
  private TextView title,
      objective,
      score,
      message,
      aimLabel,
      powerLabel,
      angleLabel,
      fire,
      cameraButton;
  private SeekBar aimBar, powerBar, angleBar;
  private boolean foreground, finishedShown;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final Runnable tick =
      new Runnable() {
        @Override
        public void run() {
          if (!foreground) return;
          refresh();
          ui.postDelayed(this, 100);
        }
      };

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    game =
        saved == null
            ? new GolfGame(GolfGame.Mode.PRACTICE)
            : (GolfGame) saved.getSerializable("game");
    if (game == null) game = new GolfGame(GolfGame.Mode.PRACTICE);
    build();
    if (saved == null) chooseMode();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    synchronized (game) {
      b.putSerializable("game", game);
    }
  }

  private int dp(int x) {
    return Math.round(x * getResources().getDisplayMetrics().density);
  }

  private TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    return t;
  }

  private TextView button(String s, Runnable action) {
    TextView t = text(s, 13, TEXT);
    t.setGravity(Gravity.CENTER);
    t.setMinHeight(dp(44));
    t.setPadding(dp(12), dp(6), dp(12), dp(6));
    t.setBackground(shape(0xe6294139));
    t.setOnClickListener(v -> action.run());
    return t;
  }

  private GradientDrawable shape(int color) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(12));
    return d;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private void build() {
    finishedShown = false;
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(0xff789f91);
    setContentView(root);
    surface = new GLSurfaceView(this);
    surface.setEGLContextClientVersion(2);
    surface.setPreserveEGLContextOnPause(true);
    renderer = new GolfRenderer(this, game);
    surface.setRenderer(renderer);
    root.addView(surface, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout hud = column();
    hud.setPadding(dp(16), dp(8), dp(16), dp(8));
    hud.setBackgroundColor(0xd9101a18);
    FrameLayout.LayoutParams top = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    root.addView(hud, top);
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.addView(button("‹ Exit", this::finish));
    row.addView(button("Games", this::chooseMode));
    title = text("  " + game.title(), 21, GREEN);
    row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    score = text("", 16, TEXT);
    row.addView(score);
    hud.addView(row);
    objective = text("", 12, TEXT);
    hud.addView(objective);
    LinearLayout bottom = column();
    bottom.setPadding(dp(16), dp(8), dp(16), dp(8));
    bottom.setBackgroundColor(0xe6101a18);
    root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
    message = text(game.message, 13, GREEN);
    bottom.addView(message);
    LinearLayout sliders = new LinearLayout(this);
    bottom.addView(sliders);
    LinearLayout aim = column();
    sliders.addView(aim, new LinearLayout.LayoutParams(0, -2, 1));
    aimLabel = text("", 11, MUTED);
    aim.addView(aimLabel);
    aimBar =
        slider(
            game.isMini() ? 360 : 90,
            game.isMini() ? 180 : 45,
            aim,
            value -> {
              synchronized (game) {
                game.aim = value - (game.isMini() ? 180 : 45);
              }
              updateLabels();
            });
    LinearLayout power = column();
    sliders.addView(power, new LinearLayout.LayoutParams(0, -2, 1));
    powerLabel = text("", 11, MUTED);
    power.addView(powerLabel);
    powerBar =
        slider(
            100,
            (int) ((game.power - (game.isMini() ? .5 : 20)) / (game.isMini() ? .095 : .65)),
            power,
            value -> {
              synchronized (game) {
                game.power = (game.isMini() ? .5 : 20) + value * (game.isMini() ? .095 : .65);
              }
              updateLabels();
            });
    if (!game.isMini()) {
      LinearLayout angle = column();
      sliders.addView(angle, new LinearLayout.LayoutParams(0, -2, .7f));
      angleLabel = text("", 11, MUTED);
      angle.addView(angleLabel);
      angleBar =
          slider(
              55,
              (int) game.launch - 5,
              angle,
              value -> {
                synchronized (game) {
                  game.launch = value + 5;
                }
                updateLabels();
              });
    } else {
      angleBar = null;
      angleLabel = null;
    }
    LinearLayout actions = new LinearLayout(this);
    actions.setGravity(Gravity.CENTER_VERTICAL);
    bottom.addView(actions);
    fire =
        button(
            game.isMini() ? "Putt" : "Hit ball",
            () -> {
              if (game.roundComplete) {
                start(game.mode);
                return;
              }
              synchronized (game) {
                if (game.holeComplete && !game.roundComplete) {
                  game.nextHole();
                  aimBar.setProgress(game.isMini() ? 180 : 45);
                } else game.shoot(game.power, game.launch, game.aim, "Touch controls");
              }
            });
    fire.setBackground(shape(GREEN));
    fire.setTextColor(0xff101a18);
    actions.addView(fire, new LinearLayout.LayoutParams(0, dp(44), 1));
    cameraButton =
        button(
            "Use live camera",
            () -> {
              synchronized (game) {
                if (game.moving || game.holeComplete || game.roundComplete) return;
              }
              Intent i = new Intent(this, LiveCameraActivity.class);
              i.putExtra("gameInput", true);
              i.putExtra("gamePutting", game.isMini());
              startActivityForResult(i, 210);
            });
    actions.addView(cameraButton, new LinearLayout.LayoutParams(0, dp(44), 1));
    actions.addView(
        button(
            game.isMini() ? "Reset ball +1" : "Target guide",
            () -> {
              if (game.isMini()) {
                synchronized (game) {
                  game.resetBall();
                }
              } else
                new AlertDialog.Builder(this)
                    .setTitle("Range targets")
                    .setMessage(
                        "GOLD · 65 m · left\n"
                            + "CORAL · 100 m · right\n"
                            + "MINT · 140 m · left\n"
                            + "BLUE · 180 m · right\n"
                            + "VIOLET · 225 m · center\n\n"
                            + "Center: 50 points\n"
                            + "Middle ring: 25 points\n"
                            + "Outer ring: 10 points\n\n"
                            + "Color Chase scores only the called target. Distance Ladder scores"
                            + " landing within ±12 m of the called distance and within the 90 m"
                            + " wide lane.")
                    .setPositiveButton("Play", null)
                    .show();
            }));
    actions.addView(button("Info", this::info));
    updateLabels();
    refresh();
  }

  private interface Change {
    void apply(int n);
  }

  private SeekBar slider(int max, int progress, LinearLayout parent, Change change) {
    SeekBar s = new SeekBar(this);
    s.setMax(max);
    s.setProgress(progress);
    parent.addView(s, new LinearLayout.LayoutParams(-1, dp(32)));
    s.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          public void onProgressChanged(SeekBar b, int p, boolean user) {
            if (user) change.apply(p);
          }

          public void onStartTrackingTouch(SeekBar b) {}

          public void onStopTrackingTouch(SeekBar b) {}
        });
    return s;
  }

  private void updateLabels() {
    if (aimLabel == null || powerLabel == null) return;
    aimLabel.setText(String.format(Locale.US, "AIM  %+.0f° · 0° forward", game.aim));
    powerLabel.setText(String.format(Locale.US, "SPEED  %.1f m/s", game.power));
    if (angleLabel != null)
      angleLabel.setText(String.format(Locale.US, "LAUNCH  %.0f°", game.launch));
  }

  private void chooseMode() {
    String[] choices = {
      "Free Practice\nUnlimited balls · explore the range",
      "Target Rush\n10 balls · score in any colored target",
      "Color Chase\n10 balls · hit the called color",
      "Distance Ladder\n6 balls · climb from 60 to 210 metres",
      "Meadow Links\n3 holes · garden island, slalom, moving gate",
      "Castle Cove\n3 holes · courtyard, moat, royal gates"
    };
    new AlertDialog.Builder(this)
        .setTitle("Fairway Play · choose a game")
        .setItems(choices, (d, i) -> start(GolfGame.Mode.values()[i]))
        .setNegativeButton("Keep playing", null)
        .show();
  }

  private void start(GolfGame.Mode mode) {
    if (surface != null) surface.onPause();
    game = new GolfGame(mode);
    build();
    if (foreground) surface.onResume();
  }

  private void info() {
    new AlertDialog.Builder(this)
        .setTitle("How to play")
        .setMessage(
            "Choose aim and speed, then hit. 0° points down the course; mini-golf supports a full"
                + " 360° aim. Walls rebound the ball. Slow down near the cup. Water returns the"
                + " ball with +1 stroke; each hole has a 12-stroke limit.\n\n"
                + "Use live camera opens the monitor and takes one compatible reading after"
                + " calibration and ball lock. Putting uses measured speed plus direction relative"
                + " to your selected aim. Range uses measured visible speed/launch angle and your"
                + " selected aim. Camera data remains experimental; game flight and roll are arcade"
                + " simulations, not real carry predictions.\n\n"
                + "3D models: Kenney Minigolf Kit 3.1, CC0. kenney.nl/assets/minigolf-kit\n"
                + "Original models and license ship with project source. Game design, range"
                + " architecture and physics are custom. No affiliation with Topgolf.")
        .setPositiveButton("Got it", null)
        .show();
  }

  private void refresh() {
    if (renderer.error != null) {
      message.setText(renderer.error);
      return;
    }
    synchronized (game) {
      objective.setText(game.objective());
      score.setText(
          game.isMini()
              ? "Strokes "
                  + game.holeStrokes
                  + " · Total "
                  + (game.totalStrokes() + (game.holeComplete ? 0 : game.holeStrokes))
              : game.points
                  + " pts · "
                  + game.shots
                  + (game.limit() > 0 ? " / " + game.limit() : " balls"));
      message.setText(game.message);
      fire.setText(
          game.roundComplete
              ? "Play again"
              : game.holeComplete
                  ? "Next hole"
                  : game.moving ? "Ball moving…" : game.isMini() ? "Putt" : "Hit ball");
      fire.setEnabled(!game.moving);
      cameraButton.setEnabled(!game.moving && !game.holeComplete && !game.roundComplete);
      if (game.roundComplete && !finishedShown) {
        finishedShown = true;
        completed();
      }
    }
  }

  private void completed() {
    String key = "best_" + game.mode.name();
    android.content.SharedPreferences prefs = getSharedPreferences("fairway", 0);
    int result = game.isMini() ? game.totalStrokes() : game.points;
    int old = prefs.getInt(key, game.isMini() ? Integer.MAX_VALUE : 0);
    boolean best = game.isMini() ? result < old : result > old;
    if (best) prefs.edit().putInt(key, result).apply();
    String summary =
        game.isMini()
            ? "Hole 1: "
                + game.card[0]
                + "\nHole 2: "
                + game.card[1]
                + "\nHole 3: "
                + game.card[2]
                + "\nTotal: "
                + result
                + " strokes"
            : "Score: " + result + " points in " + game.shots + " shots";
    new AlertDialog.Builder(this)
        .setTitle(game.title() + " · complete")
        .setMessage(summary + "\n" + (best ? "New personal best!" : "Personal best: " + old))
        .setPositiveButton("Play again", (d, w) -> start(game.mode))
        .setNeutralButton("Other games", (d, w) -> chooseMode())
        .setNegativeButton("View course", null)
        .show();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 210 || result != RESULT_OK || data == null) return;
    boolean putt = data.getBooleanExtra("putting", false);
    double speed = data.getDoubleExtra("speed", Double.NaN),
        angle = data.getDoubleExtra("angle", Double.NaN);
    synchronized (game) {
      if (putt != game.isMini()) {
        game.message = "Camera mode did not match this game. Try again.";
        return;
      }
      boolean accepted =
          game.shoot(
              speed,
              game.isMini() ? 0 : angle,
              game.isMini() ? game.aim - angle : game.aim,
              "Camera · " + data.getStringExtra("ballType"));
      if (!accepted) game.message = "Camera reading is outside this game’s range. Try again.";
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    foreground = true;
    if (surface != null) surface.onResume();
    ui.post(tick);
  }

  @Override
  protected void onPause() {
    foreground = false;
    ui.removeCallbacks(tick);
    if (surface != null) surface.onPause();
    super.onPause();
  }
}
