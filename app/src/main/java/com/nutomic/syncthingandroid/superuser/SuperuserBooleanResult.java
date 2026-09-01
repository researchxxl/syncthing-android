package com.nutomic.syncthingandroid.superuser;

import android.os.Parcel;
import android.os.Parcelable;

/** Typed boolean capability result. */
public final class SuperuserBooleanResult extends SuperuserOperationResult {

    public static final Creator<SuperuserBooleanResult> CREATOR =
            new Creator<>() {
                @Override
                public SuperuserBooleanResult createFromParcel(Parcel source) {
                    return new SuperuserBooleanResult(source);
                }

                @Override
                public SuperuserBooleanResult[] newArray(int size) {
                    return new SuperuserBooleanResult[size];
                }
            };

    public final boolean value;

    public SuperuserBooleanResult(int errorCode, String diagnostic, boolean value) {
        super(errorCode, diagnostic);
        this.value = value;
    }

    private SuperuserBooleanResult(Parcel source) {
        this(source.readInt(), source.readString(), source.readInt() != 0);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(value ? 1 : 0);
    }
}
