package com.nutomic.syncthingandroid.superuser;

import androidx.annotation.Nullable;

/**
 * Runtime privilege state separate from the durable configured-mode preference.
 *
 * <p>The diagnostic is intended for local logs and UI mapping. It must never contain credentials,
 * private key material, state-file contents, or arbitrary environment values.</p>
 */
public final class SuperuserRuntimeStatus {

    /** Runtime states exposed to lifecycle and settings code. */
    public enum State {
        NORMAL,
        AUTHORIZING,
        SUPERUSER_READY,
        SUPERUSER_UNAVAILABLE
    }

    private final State mState;
    private final SuperuserErrorCode mErrorCode;
    private final boolean mOrphanRisk;
    private final String mDiagnostic;

    private SuperuserRuntimeStatus(State state, SuperuserErrorCode errorCode,
                                   boolean orphanRisk, String diagnostic) {
        mState = state;
        mErrorCode = errorCode;
        mOrphanRisk = orphanRisk;
        mDiagnostic = diagnostic == null ? "" : diagnostic;
    }

    /** Returns the normal, unconfigured runtime state. */
    public static SuperuserRuntimeStatus normal() {
        return new SuperuserRuntimeStatus(State.NORMAL, SuperuserErrorCode.OK, false, "");
    }

    /** Returns the transient state while an explicit authorization request is in progress. */
    public static SuperuserRuntimeStatus authorizing() {
        return new SuperuserRuntimeStatus(State.AUTHORIZING, SuperuserErrorCode.OK, false, "");
    }

    /** Returns the state after the privileged service has proved UID 0. */
    public static SuperuserRuntimeStatus ready() {
        return new SuperuserRuntimeStatus(State.SUPERUSER_READY, SuperuserErrorCode.OK, false, "");
    }

    /** Returns a root-unavailable state with typed, non-sensitive diagnostic metadata. */
    public static SuperuserRuntimeStatus unavailable(SuperuserErrorCode errorCode,
                                                     boolean orphanRisk,
                                                     @Nullable String diagnostic) {
        if (errorCode == null || errorCode == SuperuserErrorCode.OK) {
            throw new IllegalArgumentException("Unavailable status requires a non-OK error code");
        }
        return new SuperuserRuntimeStatus(
                State.SUPERUSER_UNAVAILABLE, errorCode, orphanRisk, diagnostic);
    }

    public State state() {
        return mState;
    }

    public SuperuserErrorCode errorCode() {
        return mErrorCode;
    }

    public boolean orphanRisk() {
        return mOrphanRisk;
    }

    public String diagnostic() {
        return mDiagnostic;
    }
}
