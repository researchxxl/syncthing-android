package com.nutomic.syncthingandroid.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Relative paths of conflict files discovered within a configured folder. */
public final class ConflictDiscoveryResult {
    private final List<String> relativePaths;

    private ConflictDiscoveryResult(List<String> relativePaths) {
        this.relativePaths = Collections.unmodifiableList(new ArrayList<>(relativePaths));
    }

    public static ConflictDiscoveryResult of(List<String> relativePaths) {
        return new ConflictDiscoveryResult(relativePaths);
    }

    public List<String> relativePaths() {
        return relativePaths;
    }
}
