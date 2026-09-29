package com.nutomic.syncthingandroid.runtime;

import java.util.Objects;

/**
 * Identifies an existing configured folder for backend-owned folder operations.
 *
 * <p>The path is carried as the current normal-mode resolution. Callers pass this semantic
 * reference instead of exposing an arbitrary path operation on the backend.</p>
 */
public final class ConfiguredFolderReference {
    private final String id;
    private final String path;

    private ConfiguredFolderReference(String id, String path) {
        this.id = Objects.requireNonNull(id);
        this.path = Objects.requireNonNull(path);
    }

    public static ConfiguredFolderReference of(String id, String configuredPath) {
        return new ConfiguredFolderReference(id, configuredPath);
    }

    public String id() {
        return id;
    }

    String path() {
        return path;
    }
}
