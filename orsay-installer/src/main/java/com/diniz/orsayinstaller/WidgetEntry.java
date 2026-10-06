package com.diniz.orsayinstaller;

import android.net.Uri;

import java.io.File;

public final class WidgetEntry {
    public final Uri uri;
    public final File localFile;
    public final String name;
    public final long size;

    public WidgetEntry(Uri uri, String name, long size) {
        this.uri = uri;
        this.localFile = null;
        this.name = name;
        this.size = Math.max(0L, size);
    }

    public WidgetEntry(File file, String name) {
        this.uri = null;
        this.localFile = file;
        this.name = name;
        this.size = file == null ? 0L : Math.max(0L, file.length());
    }

    public String id() {
        return "w" + Integer.toUnsignedString(name.hashCode());
    }
}
