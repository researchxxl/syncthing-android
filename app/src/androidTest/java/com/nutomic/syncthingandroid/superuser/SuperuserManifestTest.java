package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertFalse;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Verifies that the UID-0 component is private to the application package. */
@RunWith(AndroidJUnit4.class)
public class SuperuserManifestTest {

    @Test
    public void rootServiceIsNotExported() throws Exception {
        Context context = InstrumentationRegistry.getTargetContext();
        ServiceInfo serviceInfo = context.getPackageManager().getServiceInfo(
                new ComponentName(context, SyncthingSuperuserService.class),
                PackageManager.GET_META_DATA);

        assertFalse(serviceInfo.exported);
    }
}
