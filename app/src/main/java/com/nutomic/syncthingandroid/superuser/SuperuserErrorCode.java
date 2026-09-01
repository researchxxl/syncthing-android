package com.nutomic.syncthingandroid.superuser;

/**
 * Stable failure vocabulary shared by the normal app process and the UID-0 service.
 *
 * <p>These values are serialized over Binder, so unknown wire codes are rejected instead of
 * being silently interpreted as success or as an unrelated failure.</p>
 */
public enum SuperuserErrorCode {
    OK(0),
    AUTHORIZATION_DENIED(1),
    ROOT_UNAVAILABLE(2),
    UID_VERIFICATION_FAILED(3),
    SERVICE_BIND_FAILED(4),
    BINDER_DIED(5),
    TIMEOUT(6),
    CORE_LAUNCH_FAILED(7),
    CORE_STOP_FAILED(8),
    STATE_ACCESS_FAILED(9),
    FOLDER_ACCESS_FAILED(10),
    STATE_TRANSFER_FAILED(11),
    OWNERSHIP_REPAIR_FAILED(12),
    SELINUX_RESTORE_FAILED(13),
    ORPHAN_RECOVERY_FAILED(14),
    INVALID_REQUEST(15);

    private final int mWireCode;

    SuperuserErrorCode(int wireCode) {
        mWireCode = wireCode;
    }

    /** Returns the stable integer used by parcelable and AIDL results. */
    public int wireCode() {
        return mWireCode;
    }

    /** Resolves a stable error code and rejects values not understood by this version. */
    public static SuperuserErrorCode fromWireCode(int wireCode) {
        for (SuperuserErrorCode errorCode : values()) {
            if (errorCode.mWireCode == wireCode) {
                return errorCode;
            }
        }
        throw new IllegalArgumentException("Unknown superuser error code: " + wireCode);
    }
}
