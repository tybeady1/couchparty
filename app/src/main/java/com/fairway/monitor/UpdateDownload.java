package com.fairway.monitor;

import java.io.*;
import java.net.*;
import java.security.*;

/** Bounded HTTPS downloads; no cleartext redirects or unbounded release payloads. */
public final class UpdateDownload {
  public static URL https(String value) throws IOException {
    URL url = new URL(value);
    if (!"https".equalsIgnoreCase(url.getProtocol())
        || url.getUserInfo() != null
        || url.getHost().isEmpty())
      throw new IOException("Use an HTTPS URL without embedded credentials.");
    return url;
  }

  public static String copy(String address, OutputStream output, long limit) throws Exception {
    URL url = https(address);
    for (int redirects = 0; redirects <= 5; redirects++) {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(15000);
      connection.setReadTimeout(20000);
      connection.setRequestProperty("User-Agent", "Fairway-Android-Updater");
      connection.setRequestProperty("Accept-Encoding", "identity");
      try {
        int code = connection.getResponseCode();
        if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
          String location = connection.getHeaderField("Location");
          if (location == null) throw new IOException("Invalid download redirect.");
          url = https(new URL(url, location).toString());
          continue;
        }
        if (code != 200) throw new IOException("Update server returned HTTP " + code);
        if (connection.getContentLengthLong() > limit)
          throw new IOException("Download exceeds size limit.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0, deadline = System.nanoTime() + 180_000_000_000L;
        try (InputStream in = connection.getInputStream()) {
          byte[] buffer = new byte[32768];
          for (int n; (n = in.read(buffer)) != -1; ) {
            if (Thread.currentThread().isInterrupted())
              throw new IOException("Download cancelled.");
            if (System.nanoTime() > deadline) throw new IOException("Download timed out.");
            count += n;
            if (count > limit) throw new IOException("Download exceeds size limit.");
            digest.update(buffer, 0, n);
            output.write(buffer, 0, n);
          }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest())
          hex.append(String.format(java.util.Locale.US, "%02x", b & 255));
        return hex.toString();
      } finally {
        connection.disconnect();
      }
    }
    throw new IOException("Too many download redirects.");
  }
}
