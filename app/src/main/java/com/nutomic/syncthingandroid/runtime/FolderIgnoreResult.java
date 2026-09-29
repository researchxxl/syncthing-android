package com.nutomic.syncthingandroid.runtime;

/** The optional ignore-list contents stored for one configured folder. */
public final class FolderIgnoreResult {
    private final String[] lines;

    private FolderIgnoreResult(String[] lines) {
        this.lines = lines == null ? null : lines.clone();
    }

    public static FolderIgnoreResult of(String[] lines) {
        return new FolderIgnoreResult(lines);
    }

    public String[] lines() {
        return lines == null ? null : lines.clone();
    }
}
