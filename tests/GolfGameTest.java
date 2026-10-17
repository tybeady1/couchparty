import com.fairway.monitor.GolfGame;

public class GolfGameTest {
  static void check(boolean ok, String why) {
    if (!ok) throw new AssertionError(why);
  }

  static void settle(GolfGame g) {
    for (int i = 0; i < 3000 && g.moving; i++) g.advance(.01);
    check(!g.moving, "must settle");
  }

  public static void main(String[] args) {
    check(
        GolfGame.ringScore(5) == 50
            && GolfGame.ringScore(11) == 25
            && GolfGame.ringScore(18) == 10
            && GolfGame.ringScore(19) == 0,
        "rings");
    GolfGame r = new GolfGame(GolfGame.Mode.TARGET_RUSH);
    check(!r.shoot(Double.NaN, 22, 0, "test"), "reject NaN");
    for (int i = 0; i < 10; i++) {
      check(r.shoot(55, 22, 0, "test"), "shot accepted");
      check(!r.shoot(55, 22, 0, "test"), "no double hit");
      settle(r);
    }
    check(r.roundComplete && r.shots == 10 && r.lastDistance > 0, "range round");
    check(!r.shoot(55, 22, 0, "test"), "finished round blocked");
    GolfGame m = new GolfGame(GolfGame.Mode.MEADOW);
    m.x = 5.6;
    m.z = 3;
    m.shoot(4, 0, 90, "test");
    m.advance(.1);
    check(m.vx < 0 && m.x <= 6 - GolfGame.BALL, "rail bounce");
    settle(m);
    m.x = 0;
    m.z = 9;
    m.shoot(4, 0, 0, "test");
    for (int i = 0; i < 8; i++) m.advance(.1);
    check(m.z < 10.5, "island blocks ball");
    settle(m);
    for (int h = 0; h < 3; h++) {
      m.x = m.hole().cupX;
      m.z = m.hole().cupZ - .3;
      check(m.shoot(.5, 0, 0, "test"), "cup shot");
      settle(m);
      check(m.holeComplete, "cup capture");
      if (h < 2) m.nextHole();
    }
    check(m.roundComplete && m.totalStrokes() == 5, "scorecard");
    GolfGame c = new GolfGame(GolfGame.Mode.CASTLE);
    for (int i = 0; i < 12; i++) c.resetBall();
    c.nextHole();
    c.x = -2;
    c.z = 10;
    c.shoot(4, 0, 0, "test");
    settle(c);
    check(c.holeStrokes == 2 && c.z == 10, "water penalty and return");
    System.out.println("GolfGameTest passed");
  }
}
