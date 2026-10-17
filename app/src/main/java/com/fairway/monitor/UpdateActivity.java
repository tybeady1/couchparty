package com.fairway.monitor;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Explicit user-triggered checks, verified download and Android-confirmed installation. */
public class UpdateActivity extends Activity {
  private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
  private EditText feed;
  private TextView status;
  private Button check, download, install;
  private JSONObject available;
  private android.content.SharedPreferences prefs;
  private File apk;

  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    prefs = getSharedPreferences("fairway", 0);
    apk = new File(getFilesDir(), "updates/update.apk");
    ScrollView scroll = new ScrollView(this);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(32, 24, 32, 24);
    scroll.addView(body);
    setContentView(scroll);
    button(body, "Back", this::finish);
    try {
      label(
          body,
          "Fairway " + installed().versionName + " (build " + installed().versionCode + ")",
          24);
    } catch (Exception e) {
      label(body, "Fairway updates", 24);
    }
    label(
        body,
        "Check for updates, download, then confirm installation in Android. Your sessions stay on"
            + " this phone.",
        16);
    label(body, "Update feed URL (HTTPS)", 16);
    feed = new EditText(this);
    feed.setSingleLine(true);
    feed.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    feed.setHint("https://…/releases/latest/download/update.json");
    String defaultFeed = "";
    try (InputStream in = getAssets().open("update-url.txt")) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      byte[] b = new byte[1024];
      for (int n; (n = in.read(b)) != -1; ) bytes.write(b, 0, n);
      defaultFeed = bytes.toString("UTF-8").trim();
    } catch (IOException ignored) {
    }
    feed.setText(prefs.getString("updateFeed", defaultFeed));
    body.addView(feed);
    check = button(body, "Save source & check for updates", this::check);
    download = button(body, "Download update", this::download);
    download.setEnabled(false);
    install = button(body, "Install downloaded update", this::install);
    install.setEnabled(apk.exists() && prefs.contains("downloadedUpdate"));
    status =
        label(
            body,
            feed.getText().length() == 0
                ? "No release host configured yet. Enter the published update feed URL."
                : "Ready to check. No automatic downloads.",
            16);
  }

  private TextView label(LinearLayout p, String s, int size) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setPadding(0, 16, 0, 16);
    p.addView(v);
    return v;
  }

  private Button button(LinearLayout p, String s, Runnable r) {
    Button b = new Button(this);
    b.setText(s);
    b.setOnClickListener(v -> r.run());
    p.addView(b);
    return b;
  }

  private void ui(Runnable r) {
    runOnUiThread(
        () -> {
          if (!isFinishing() && !isDestroyed()) r.run();
        });
  }

  private void busy(String message) {
    status.setText(message);
    check.setEnabled(false);
    download.setEnabled(false);
    install.setEnabled(false);
    feed.setEnabled(false);
  }

  private void done(String message) {
    status.setText(message);
    check.setEnabled(true);
    feed.setEnabled(true);
    download.setEnabled(available != null);
    install.setEnabled(apk.exists() && prefs.contains("downloadedUpdate"));
  }

  private PackageInfo installed() throws Exception {
    return getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
  }

  private void validateManifest(JSONObject j) throws Exception {
    if (!getPackageName().equals(j.getString("packageName")))
      throw new IOException("Update is for a different app.");
    if (j.getLong("versionCode") < 1 || j.getLong("versionCode") > Integer.MAX_VALUE)
      throw new IOException("Invalid version code.");
    if (j.getString("versionName").length() > 80) throw new IOException("Invalid version name.");
    if (!j.getString("sha256").matches("[0-9a-fA-F]{64}"))
      throw new IOException("Missing or invalid SHA-256 checksum.");
    UpdateDownload.https(j.getString("apkUrl"));
  }

  private void check() {
    final String address = feed.getText().toString().trim();
    try {
      UpdateDownload.https(address);
    } catch (Exception e) {
      status.setText(e.getMessage());
      return;
    }
    prefs.edit().putString("updateFeed", address).apply();
    available = null;
    busy("Checking for updates…");
    WORK.execute(
        () -> {
          try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            UpdateDownload.copy(address, bytes, 65536);
            JSONObject j = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            validateManifest(j);
            boolean newer = j.getLong("versionCode") > installed().versionCode;
            ui(
                () -> {
                  available = newer ? j : null;
                  done(
                      newer
                          ? "Available: "
                              + j.optString("versionName")
                              + " (build "
                              + j.optInt("versionCode")
                              + ")\n\n"
                              + j.optString("notes", "No release notes.")
                          : "You are up to date. Installed version is the same or newer than this"
                                + " feed.");
                });
          } catch (Exception e) {
            ui(() -> done("Could not check for updates: " + e.getMessage()));
          }
        });
  }

  private void download() {
    final JSONObject release = available;
    if (release == null) return;
    busy("Downloading update… Keep this screen open. Nothing is installed automatically.");
    WORK.execute(
        () -> {
          File partial = new File(apk.getParentFile(), "download.part");
          try {
            if (!apk.getParentFile().isDirectory() && !apk.getParentFile().mkdirs())
              throw new IOException("Cannot create update directory.");
            String hash;
            try (OutputStream out = new FileOutputStream(partial)) {
              hash = UpdateDownload.copy(release.getString("apkUrl"), out, 100L * 1024 * 1024);
            }
            if (!hash.equalsIgnoreCase(release.getString("sha256")))
              throw new IOException("Checksum mismatch; download rejected.");
            validateApk(partial, release);
            java.nio.file.Files.move(
                partial.toPath(), apk.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            prefs.edit().putString("downloadedUpdate", release.toString()).commit();
            ui(
                () ->
                    done(
                        "Downloaded and verified "
                            + release.optString("versionName")
                            + ". Tap Install downloaded update."));
          } catch (Exception e) {
            partial.delete();
            ui(() -> done("Update download failed: " + e.getMessage()));
          }
        });
  }

  private void validateApk(File file, JSONObject release) throws Exception {
    PackageInfo candidate =
        getPackageManager()
            .getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNATURES);
    PackageInfo current = installed();
    if (candidate == null || !current.packageName.equals(candidate.packageName))
      throw new IOException("Invalid APK or wrong app.");
    if (candidate.versionCode <= current.versionCode
        || candidate.versionCode != release.getInt("versionCode")
        || !Objects.equals(candidate.versionName, release.getString("versionName")))
      throw new IOException("APK version does not match a newer release.");
    if (candidate.signatures == null
        || current.signatures == null
        || !new HashSet<>(Arrays.asList(candidate.signatures))
            .equals(new HashSet<>(Arrays.asList(current.signatures))))
      throw new IOException("Signing key differs from the installed app; update rejected.");
  }

  private void install() {
    try {
      JSONObject release = new JSONObject(prefs.getString("downloadedUpdate", "{}"));
      validateManifest(release);
      validateApk(apk, release);
      if (!getPackageManager().canRequestPackageInstalls()) {
        new AlertDialog.Builder(this)
            .setTitle("Allow Fairway to install updates")
            .setMessage(
                "Enable Allow from this source in Android settings, return here, then tap Install"
                    + " downloaded update again.")
            .setPositiveButton(
                "Open Android settings",
                (d, w) ->
                    startActivity(
                        new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName()))))
            .setNegativeButton("Cancel", null)
            .show();
        return;
      }
      Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
      intent.setDataAndType(UpdateProvider.URI, "application/vnd.android.package-archive");
      intent.setClipData(ClipData.newRawUri("Fairway update", UpdateProvider.URI));
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      startActivity(intent);
    } catch (Exception e) {
      status.setText("Cannot install update: " + e.getMessage());
    }
  }
}
