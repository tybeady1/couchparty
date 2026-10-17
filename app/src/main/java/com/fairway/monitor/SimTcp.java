package com.fairway.monitor;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/** One writer; bounded replies and timeouts. A failed write is never replayed automatically. */
public final class SimTcp implements Closeable {
  private volatile Socket socket;
  private volatile boolean cancelled;
  private String endpoint;
  private static final Pattern CODE = Pattern.compile("\"Code\"\\s*:\\s*(\\d+)");

  public void connect(String host, int port) throws IOException {
    if (cancelled) throw new IOException("Connection cancelled");
    if (host == null || host.trim().isEmpty() || host.contains("://") || port < 1 || port > 65535)
      throw new IOException("Enter a host/IP (without http://) and port 1–65535");
    String next = host + ":" + port;
    Socket s = socket;
    if (s != null && !s.isClosed() && next.equals(endpoint)) return;
    close();
    s = new Socket();
    socket = s;
    try {
      if (cancelled) throw new IOException("Connection cancelled");
      s.connect(new InetSocketAddress(host, port), 2500);
      s.setSoTimeout(2500);
      s.setTcpNoDelay(true);
      endpoint = next;
    } catch (IOException e) {
      close();
      throw e;
    }
  }

  public String send(String host, int port, SimProtocol.Target target, String payload)
      throws IOException {
    connect(host, port);
    Socket s = socket;
    if (s == null) throw new IOException("Connection cancelled");
    try {
      if (cancelled) throw new IOException("Connection cancelled");
      if (!target.openConnect()) {
        // These adapters do not consume result/player events, but must not leave
        // an ever-growing reply backlog on a long-running game connection.
        InputStream in = s.getInputStream();
        byte[] discard = new byte[4096];
        int drained = 0;
        while (in.available() > 0 && drained < 65536) {
          int n = in.read(discard, 0, Math.min(discard.length, in.available()));
          if (n <= 0) break;
          drained += n;
        }
      }
      s.getOutputStream().write((payload + "\n").getBytes(StandardCharsets.UTF_8));
      s.getOutputStream().flush();
      if (!target.openConnect()) return "Written to receiver; no shot acknowledgement defined";
      long deadline = System.nanoTime() + 2500000000L;
      for (int messages = 0; messages < 16; messages++) {
        String response = readObject(s, deadline);
        Matcher matcher = CODE.matcher(response);
        if (!matcher.find()) continue;
        int code = Integer.parseInt(matcher.group(1));
        if (code == 200) return "Receiver acknowledged shot (200)";
        if (code >= 400) {
          close();
          return "Receiver rejected shot (" + code + ")";
        }
        // 201 is a player event, not acknowledgement. User controls camera mode/club.
      }
      close();
      return "Written; acknowledgement unknown (too many receiver events)";
    } catch (SocketTimeoutException e) {
      close();
      return "Written; acknowledgement timed out. Not resent to avoid duplicate shots";
    } catch (IOException e) {
      close();
      throw e;
    }
  }

  private static String readObject(Socket s, long deadline) throws IOException {
    StringBuilder b = new StringBuilder();
    int depth = 0;
    boolean quoted = false, escaped = false;
    InputStream in = s.getInputStream();
    while (b.length() < 16384) {
      long remaining = (deadline - System.nanoTime()) / 1000000;
      if (remaining <= 0) throw new SocketTimeoutException("Reply timeout");
      s.setSoTimeout((int) Math.max(1, remaining));
      int c = in.read();
      if (c == -1) throw new EOFException("Receiver closed connection; delivery unknown");
      if (depth == 0) {
        if (Character.isWhitespace(c)) continue;
        if (c != '{') throw new IOException("Invalid receiver reply");
      }
      b.append((char) c);
      if (quoted) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == '"') quoted = false;
      } else if (c == '"') quoted = true;
      else if (c == '{') depth++;
      else if (c == '}' && --depth == 0) return b.toString();
    }
    throw new IOException("Receiver reply exceeds 16 KiB");
  }

  @Override
  public void close() {
    Socket old = socket;
    socket = null;
    endpoint = null;
    if (old != null)
      try {
        old.close();
      } catch (IOException ignored) {
      }
  }

  public void cancel() {
    cancelled = true;
    close();
  }
}
