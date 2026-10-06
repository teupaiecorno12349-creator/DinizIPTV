package com.diniz.orsayinstaller;

import android.net.Uri;

public final class WidgetEntry {
    public final Uri uri;
    public final String name;
    public final long size;

    public WidgetEntry(Uri uri, String name, long size) {
        this.uri = uri;
        this.name = name;
        this.size = Math.max(0L, size);
    }

    public String id() {
        return "w" + Integer.toUnsignedString(name.hashCode());
    }
}
