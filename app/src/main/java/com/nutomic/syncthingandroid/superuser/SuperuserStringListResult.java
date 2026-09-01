package com.nutomic.syncthingandroid.superuser;

import android.os.Parcel;
import android.os.Parcelable;

/** Typed string-list capability result. */
public final class SuperuserStringListResult extends SuperuserOperationResult {

    public static final Creator<SuperuserStringListResult> CREATOR =
            new Creator<>() {
                @Override
                public SuperuserStringListResult createFromParcel(Parcel source) {
                    return new SuperuserStringListResult(source);
                }

                @Override
                public SuperuserStringListResult[] newArray(int size) {
                    return new SuperuserStringListResult[size];
                }
            };

    public final String[] values;

    public SuperuserStringListResult(int errorCode, String diagnostic, String[] values) {
        super(errorCode, diagnostic);
        this.values = values == null ? new String[0] : values.clone();
    }

    private SuperuserStringListResult(Parcel source) {
        this(source.readInt(), source.readString(), source.createStringArray());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeStringArray(values);
    }
}
