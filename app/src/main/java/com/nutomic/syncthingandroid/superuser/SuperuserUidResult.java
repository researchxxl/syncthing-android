package com.nutomic.syncthingandroid.superuser;

import android.os.Parcel;
import android.os.Parcelable;

/** Typed UID-verification result returned by the privileged service. */
public final class SuperuserUidResult extends SuperuserOperationResult {

    public static final Creator<SuperuserUidResult> CREATOR =
            new Creator<>() {
                @Override
                public SuperuserUidResult createFromParcel(Parcel source) {
                    return new SuperuserUidResult(source);
                }

                @Override
                public SuperuserUidResult[] newArray(int size) {
                    return new SuperuserUidResult[size];
                }
            };

    public final int uid;

    public SuperuserUidResult(int errorCode, String diagnostic, int uid) {
        super(errorCode, diagnostic);
        this.uid = uid;
    }

    private SuperuserUidResult(Parcel source) {
        super(source);
        uid = source.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(uid);
    }
}
