package com.nutomic.syncthingandroid.root;

import eu.chainfire.libsuperuser.Shell;

public final class RootAccess {

    private RootAccess() {
    }

    public static boolean isRootAvailableBlocking() {
        return Shell.SU.available();
    }
}
