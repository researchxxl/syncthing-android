package com.nutomic.syncthingandroid.superuser;

/** Immutable identity tuple required before a supervised process may be signaled. */
public record ProcessIdentity(int pid, long startTimeTicks, String executable) {

    public ProcessIdentity {
        if (pid <= 0) {
            throw new IllegalArgumentException("pid must be positive");
        }
        if (startTimeTicks <= 0) {
            throw new IllegalArgumentException("startTimeTicks must be positive");
        }
        if (executable == null || executable.isEmpty()) {
            throw new IllegalArgumentException("executable must not be empty");
        }
    }
}
