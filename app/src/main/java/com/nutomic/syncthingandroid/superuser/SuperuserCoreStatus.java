package com.nutomic.syncthingandroid.superuser;

import android.os.Parcel;
import android.os.Parcelable;

/** Snapshot of the single supervised Syncthing core process. */
public final class SuperuserCoreStatus extends SuperuserOperationResult {

    public static final Creator<SuperuserCoreStatus> CREATOR =
            new Creator<>() {
                @Override
                public SuperuserCoreStatus createFromParcel(Parcel source) {
                    return new SuperuserCoreStatus(source);
                }

                @Override
                public SuperuserCoreStatus[] newArray(int size) {
                    return new SuperuserCoreStatus[size];
                }
            };

    public final boolean running;
    public final int pid;

    public SuperuserCoreStatus(int errorCode, String diagnostic, boolean running, int pid) {
        super(errorCode, diagnostic);
        this.running = running;
        this.pid = running ? pid : -1;
    }

    private SuperuserCoreStatus(Parcel source) {
        this(source.readInt(), source.readString(), source.readInt() != 0, source.readInt());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(running ? 1 : 0);
        dest.writeInt(pid);
    }
}
