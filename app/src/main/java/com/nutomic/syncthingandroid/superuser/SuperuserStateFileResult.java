package com.nutomic.syncthingandroid.superuser;

import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

/** Typed result containing a descriptor for one fixed state file. */
public final class SuperuserStateFileResult extends SuperuserOperationResult {

    public static final Creator<SuperuserStateFileResult> CREATOR =
            new Creator<>() {
                @Override
                public SuperuserStateFileResult createFromParcel(Parcel source) {
                    return new SuperuserStateFileResult(source);
                }

                @Override
                public SuperuserStateFileResult[] newArray(int size) {
                    return new SuperuserStateFileResult[size];
                }
            };

    public final ParcelFileDescriptor descriptor;

    public SuperuserStateFileResult(int errorCode, String diagnostic,
                                    ParcelFileDescriptor descriptor) {
        super(errorCode, diagnostic);
        this.descriptor = descriptor;
    }

    private SuperuserStateFileResult(Parcel source) {
        this(source.readInt(), source.readString(),
                source.readParcelable(ParcelFileDescriptor.class.getClassLoader()));
    }

    @Override
    public int describeContents() {
        return descriptor == null ? 0 : CONTENTS_FILE_DESCRIPTOR;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(descriptor, flags);
    }
}
