package com.nutomic.syncthingandroid.superuser;

import android.os.Parcel;
import android.os.Parcelable;

/** Typed result for a privileged operation with no additional return value. */
public class SuperuserOperationResult implements Parcelable {

    public static final Creator<SuperuserOperationResult> CREATOR =
            new Creator<>() {
                @Override
                public SuperuserOperationResult createFromParcel(Parcel source) {
                    return new SuperuserOperationResult(source);
                }

                @Override
                public SuperuserOperationResult[] newArray(int size) {
                    return new SuperuserOperationResult[size];
                }
            };

    public final int errorCode;
    public final String diagnostic;

    public SuperuserOperationResult(int errorCode, String diagnostic) {
        SuperuserErrorCode.fromWireCode(errorCode);
        this.errorCode = errorCode;
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    protected SuperuserOperationResult(Parcel source) {
        this(source.readInt(), source.readString());
    }

    public static SuperuserOperationResult success() {
        return new SuperuserOperationResult(SuperuserErrorCode.OK.wireCode(), "");
    }

    public static SuperuserOperationResult failure(SuperuserErrorCode errorCode, String diagnostic) {
        if (errorCode == null || errorCode == SuperuserErrorCode.OK) {
            throw new IllegalArgumentException("A failure requires a non-OK error code");
        }
        return new SuperuserOperationResult(errorCode.wireCode(), diagnostic);
    }

    public boolean isSuccess() {
        return errorCode == SuperuserErrorCode.OK.wireCode();
    }

    public SuperuserErrorCode error() {
        return SuperuserErrorCode.fromWireCode(errorCode);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(errorCode);
        dest.writeString(diagnostic);
    }
}
