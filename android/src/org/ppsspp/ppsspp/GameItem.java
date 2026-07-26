package org.ppsspp.ppsspp;

import android.net.Uri;

public class GameItem {

    public final String displayName;
    public final Uri contentUri;
    public final long sizeBytes;
    public final String extension;
    public final Uri coverUri;

    public GameItem(String displayName, Uri contentUri, long sizeBytes, String extension, Uri coverUri) {
        this.displayName = displayName;
        this.contentUri = contentUri;
        this.sizeBytes = sizeBytes;
        this.extension = extension;
        this.coverUri = coverUri;
    }

    public String getTitle() {
        int dot = displayName.lastIndexOf('.');
        return dot > 0 ? displayName.substring(0, dot) : displayName;
    }

    public String getSizeLabel() {
        double mb = sizeBytes / (1024.0 * 1024.0);
        if (mb > 1024) {
            return String.format("%.1f GB", mb / 1024.0);
        }
        return String.format("%.0f MB", mb);
    }
}
