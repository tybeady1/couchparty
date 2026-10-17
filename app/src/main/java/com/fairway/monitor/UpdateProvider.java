package com.fairway.monitor;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Read-only, single-file grant for Android's package installer. */
public class UpdateProvider extends ContentProvider {
  public static final Uri URI = Uri.parse("content://com.fairway.monitor.updates/update.apk");

  public boolean onCreate() {
    return true;
  }

  private File file(Uri uri) {
    if (!URI.equals(uri)) throw new IllegalArgumentException("Unknown update file");
    return new File(getContext().getFilesDir(), "updates/update.apk");
  }

  public String getType(Uri uri) {
    file(uri);
    return "application/vnd.android.package-archive";
  }

  public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
    if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
    return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
  }

  public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
    File f = file(uri);
    String[] columns =
        projection == null
            ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
            : projection;
    MatrixCursor cursor = new MatrixCursor(columns);
    Object[] row = new Object[columns.length];
    for (int i = 0; i < columns.length; i++) {
      if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "Fairway-update.apk";
      if (OpenableColumns.SIZE.equals(columns[i])) row[i] = f.length();
    }
    cursor.addRow(row);
    return cursor;
  }

  public Uri insert(Uri uri, ContentValues values) {
    throw new UnsupportedOperationException();
  }

  public int update(Uri uri, ContentValues values, String s, String[] args) {
    throw new UnsupportedOperationException();
  }

  public int delete(Uri uri, String s, String[] args) {
    throw new UnsupportedOperationException();
  }
}
