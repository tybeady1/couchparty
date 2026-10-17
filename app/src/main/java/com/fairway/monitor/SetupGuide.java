package com.fairway.monitor;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.widget.*;

/** Code-native setup illustration, shared with the live monitor's help flow. */
public final class SetupGuide {
  public static void show(Context context, boolean putting) {
    LinearLayout body = new LinearLayout(context);
    body.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
    body.setPadding(pad, pad, pad, pad);
    body.addView(new Diagram(context, putting), new LinearLayout.LayoutParams(-1, -2));
    TextView instructions = new TextView(context);
    instructions.setTextSize(15);
    instructions.setTextColor(0xfff2f5eb);
    instructions.setText(
        putting
            ? "1. Secure the phone above the mat, lens pointing straight down. Keep it level and"
                + " still.\n\n"
                + "2. Align the putting line horizontally across the preview. Place the ball near"
                + " one side, leaving room to roll.\n\n"
                + "3. Measure the full visible image width on the mat, excluding black borders."
                + " Enter that width in metres.\n\n"
                + "4. Choose your ball type, tap the ball in the live view, and wait for READY."
                + " Putt normally; the app captures the movement and re-arms when you replace the"
                + " ball.\n\n"
                + "Direction is relative to the horizontal image line. Distance shown by the"
                + " setup is calibration, not a prediction of total roll."
            : "1. Put the phone on a fixed tripod beside the ball, lens at ball height and"
                + " perpendicular to the shot line. Keep the phone level.\n\n"
                + "2. Frame the ball near one side with space for the first part of its flight."
                + " Use bright light and a darker background.\n\n"
                + "3. Measure the full visible image width at the ball’s flight plane. Enter it"
                + " in metres; do not enter the distance from phone to ball.\n\n"
                + "4. Choose your ball type, tap the stationary ball, and wait for READY before"
                + " swinging. The trigger follows ball movement, not a practice swing.\n\n"
                + "A normal 30–60 fps stream may miss fast golf shots. An incomplete track"
                + " produces no reading. Carry and spin are not measured.");
    body.addView(instructions);
    ScrollView scroll = new ScrollView(context);
    scroll.addView(body);
    new AlertDialog.Builder(context)
        .setTitle(putting ? "Phone placement · putting" : "Phone placement · full swing")
        .setView(scroll)
        .setPositiveButton("Got it", null)
        .setNeutralButton(
            putting ? "Show swing setup" : "Show putting setup", (d, w) -> show(context, !putting))
        .show();
  }

  private static final class Diagram extends View {
    private final boolean putting;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    Diagram(Context c, boolean putting) {
      super(c);
      this.putting = putting;
      setContentDescription(
          putting
              ? "Side view: phone directly above putting mat, camera pointing down, ball rolling"
                  + " horizontally. Measure the visible width on the mat."
              : "Top view: phone beside shot line, pointing at a right angle to ball travel."
                  + " Measure visible width along the ball flight plane.");
    }

    @Override
    protected void onMeasure(int w, int h) {
      int width = MeasureSpec.getSize(w);
      setMeasuredDimension(width, Math.round(width * 360f / 640));
    }

    private void label(Canvas c, String s, float x, float y, int size, int color) {
      p.setColor(color);
      p.setTextSize(size);
      p.setStyle(Paint.Style.FILL);
      c.drawText(s, x, y, p);
    }

    private void line(Canvas c, float x, float y, float xx, float yy, int color) {
      p.setColor(color);
      p.setStrokeWidth(3);
      c.drawLine(x, y, xx, yy, p);
    }

    private void arrow(Canvas c, float x, float y, float xx, float yy) {
      line(c, x, y, xx, yy, 0xffcbf078);
      double a = Math.atan2(yy - y, xx - x);
      line(
          c,
          xx,
          yy,
          xx - (float) Math.cos(a - .5) * 14,
          yy - (float) Math.sin(a - .5) * 14,
          0xffcbf078);
      line(
          c,
          xx,
          yy,
          xx - (float) Math.cos(a + .5) * 14,
          yy - (float) Math.sin(a + .5) * 14,
          0xffcbf078);
    }

    @Override
    protected void onDraw(Canvas c) {
      c.save();
      c.scale(getWidth() / 640f, getHeight() / 360f);
      c.drawColor(0xff101a18);
      label(c, putting ? "PUTTING · SIDE VIEW" : "FULL SWING · TOP VIEW", 20, 30, 17, 0xffcbf078);
      float phoneY = putting ? 92 : 255, ballY = putting ? 245 : 110;
      Path cone = new Path();
      cone.moveTo(320, phoneY);
      cone.lineTo(110, ballY);
      cone.lineTo(540, ballY);
      cone.close();
      p.setColor(0xff243d2c);
      c.drawPath(cone, p);
      p.setColor(0xff415b4c);
      c.drawRoundRect(75, ballY + 12, 570, ballY + 29, 6, 6, p);
      p.setColor(Color.WHITE);
      c.drawCircle(145, ballY, 10, p);
      arrow(c, 173, ballY, 530, ballY);
      label(
          c,
          putting ? "Ball rolls across mat" : "Ball flight / shot line",
          180,
          ballY - 20,
          17,
          0xfff2f5eb);
      p.setColor(0xffcbf078);
      c.drawRoundRect(286, phoneY - 24, 354, phoneY + 18, 7, 7, p);
      p.setColor(0xff101a18);
      c.drawCircle(300, phoneY - 10, 5, p);
      label(c, "PHONE", 365, phoneY + 3, 17, 0xfff2f5eb);
      arrow(c, 320, putting ? phoneY + 26 : phoneY - 32, 320, putting ? phoneY + 78 : phoneY - 84);
      label(
          c,
          putting ? "Lens straight down" : "Lens at ball height",
          20,
          putting ? 85 : 260,
          16,
          0xff9daea0);
      if (!putting) {
        line(c, 320, ballY, 320, ballY + 18, 0xff9daea0);
        line(c, 320, ballY + 18, 338, ballY + 18, 0xff9daea0);
        label(c, "90°", 345, ballY + 38, 15, 0xff9daea0);
      }
      float dim = putting ? 305 : 175;
      line(c, 110, dim, 540, dim, 0xff9daea0);
      line(c, 110, dim - 7, 110, dim + 7, 0xff9daea0);
      line(c, 540, dim - 7, 540, dim + 7, 0xff9daea0);
      label(c, "Measure visible width at the ball plane", 155, dim + 25, 16, 0xfff2f5eb);
      c.restore();
    }
  }
}
