import com.fairway.monitor.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class SimIntegrationTest {
  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    SimProtocol.Assumptions strict = new SimProtocol.Assumptions(false, false, 2, 3500, -4);
    SimProtocol.Assumptions games = new SimProtocol.Assumptions(true, false, 2, 3500, -4);
    SimProtocol.Reading swing = new SimProtocol.Reading(7, 100, false, 44.704, 15, BallType.FOAM);
    String raw = SimProtocol.encode(SimProtocol.Target.RAW, swing, strict);
    require(
        raw.contains("\"spin_rpm\":null") && raw.contains("\"carry_m\":null"),
        "Raw must preserve unknowns");
    require(
        raw.contains("\"ball_type\":\"FOAM\"") && raw.contains("44.704000"),
        "Raw provenance/units");
    for (SimProtocol.Target target : SimProtocol.Target.values()) {
      if (target == SimProtocol.Target.RAW) continue;
      try {
        SimProtocol.encode(target, swing, strict);
        throw new AssertionError("Missing data not blocked");
      } catch (IllegalArgumentException expected) {
      }
    }
    String open = SimProtocol.encode(SimProtocol.Target.OPENSHOT, swing, games);
    require(open.contains("\"Speed\":100.000000"), "Open Connect requires mph");
    require(
        open.contains("\"TotalSpin\":3500.000000") && open.contains("\"HLA\":2.000000"),
        "Explicit assumptions");
    require(
        !open.contains("CarryDistance") && !open.contains("\"ClubData\":"),
        "No invented carry or club");
    String ogs = SimProtocol.encode(SimProtocol.Target.OGS, swing, games);
    require(
        ogs.contains("\"unit\":\"metric\"") && ogs.contains("\"ballSpeed\":44.704000"),
        "OGS units");
    SimProtocol.Reading putt = new SimProtocol.Reading(8, 101, true, 1.5, -6, BallType.NORMAL);
    String puttWire =
        SimProtocol.encode(
            SimProtocol.Target.HECKLE, putt, new SimProtocol.Assumptions(true, true, 5, 4000, 10));
    require(
        puttWire.contains("\"HLA\":6.000000")
            && puttWire.contains("\"VLA\":0.000000")
            && puttWire.contains("\"TotalSpin\":0.000000"),
        "Putt direction inversion and explicit zero assumptions");
    try {
      SimProtocol.encode(
          SimProtocol.Target.OGS,
          new SimProtocol.Reading(1, 1, false, 1, -10, BallType.NORMAL),
          games);
      throw new AssertionError("Angle silently clamped");
    } catch (IllegalArgumentException expected) {
    }
    for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
      try {
        new SimProtocol.Reading(1, 1, false, invalid, 0, BallType.NORMAL);
        throw new AssertionError("Bad speed");
      } catch (IllegalArgumentException expected) {
      }
    }
    // Exercise actual TCP stream fragmentation, adjacent player/ack JSON, and rejection.
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        SimTcp client = new SimTcp()) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      try {
        Future<?> mock =
            worker.submit(
                () -> {
                  try (Socket peer = server.accept()) {
                    BufferedReader reader =
                        new BufferedReader(
                            new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                    require(open.equals(reader.readLine()), "Payload changed on wire");
                    OutputStream out = peer.getOutputStream();
                    out.write(
                        "{\"Code\":201,\"Player\":{\"Club\":\"PT\"}}{\"Co"
                            .getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Thread.sleep(30);
                    out.write("de\":200}".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    require(open.equals(reader.readLine()), "Second shot changed");
                    out.write("{\"Code\":501}".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        String answer =
            client.send("127.0.0.1", server.getLocalPort(), SimProtocol.Target.OPENSHOT, open);
        require(answer.contains("acknowledged"), "Fragmented acknowledgment");
        require(
            client
                .send("127.0.0.1", server.getLocalPort(), SimProtocol.Target.OPENSHOT, open)
                .contains("rejected"),
            "Server rejection");
        mock.get(5, TimeUnit.SECONDS);
      } finally {
        worker.shutdownNow();
      }
    }
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        SimTcp client = new SimTcp()) {
      ExecutorService worker = Executors.newSingleThreadExecutor();
      try {
        Future<?> mock =
            worker.submit(
                () -> {
                  try (Socket peer = server.accept()) {
                    new BufferedReader(new InputStreamReader(peer.getInputStream())).readLine();
                    Thread.sleep(2800);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        require(
            client
                .send("127.0.0.1", server.getLocalPort(), SimProtocol.Target.OPENCONNECT, open)
                .contains("timed out"),
            "No false success without acknowledgment");
        mock.get(5, TimeUnit.SECONDS);
      } finally {
        worker.shutdownNow();
      }
    }
    SimTcp cancelled = new SimTcp();
    cancelled.cancel();
    try {
      cancelled.connect("127.0.0.1", 921);
      throw new AssertionError("Cancelled connection reopened");
    } catch (IOException expected) {
    }
    System.out.println(
        "PASS: protocol units, missing metrics, assumptions, angle rejection, TCP"
            + " framing/ack/rejection/timeout/cancellation");
  }
}
