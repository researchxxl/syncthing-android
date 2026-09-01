package com.nutomic.syncthingandroid.superuser;

import android.os.Parcel;
import android.os.Parcelable;

import java.nio.charset.StandardCharsets;

/** Typed exit result for the supervised Syncthing core. */
public final class SuperuserCoreExitResult extends SuperuserOperationResult {

    private static final int MAX_STDOUT_BYTES = 64 * 1024;

    public static final Creator<SuperuserCoreExitResult> CREATOR =
            new Creator<>() {
                @Override
                public SuperuserCoreExitResult createFromParcel(Parcel source) {
                    return new SuperuserCoreExitResult(source);
                }

                @Override
                public SuperuserCoreExitResult[] newArray(int size) {
                    return new SuperuserCoreExitResult[size];
                }
            };

    public final int exitCode;
    public final String stdout;

    public SuperuserCoreExitResult(int errorCode, String diagnostic, int exitCode, String stdout) {
        super(errorCode, diagnostic);
        this.exitCode = exitCode;
        this.stdout = bound(stdout);
    }

    private SuperuserCoreExitResult(Parcel source) {
        this(source.readInt(), source.readString(), source.readInt(), source.readString());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(exitCode);
        dest.writeString(stdout);
    }

    private static String bound(String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_STDOUT_BYTES) {
            return value == null ? "" : value;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new String(bytes, 0, MAX_STDOUT_BYTES, StandardCharsets.UTF_8);
    }
}
