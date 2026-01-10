package com.nutomic.syncthingandroid;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.StrictMode;

import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.nutomic.syncthingandroid.service.Constants;

import javax.inject.Inject;

public class SyncthingApp extends Application {

    @Inject DaggerComponent mComponent;

    @Override
    public void onCreate() {
        super.onCreate();

        DaggerDaggerComponent.builder()
                .syncthingModule(new SyncthingModule(this))
                .build()
                .inject(this);

        // Set VM policy to avoid crash when sending folder URI to file manager.
        StrictMode.VmPolicy vmPolicy = new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build();
        StrictMode.setVmPolicy(vmPolicy);

        /*
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
        {
            StrictMode.ThreadPolicy threadPolicy = new StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build();
            StrictMode.setThreadPolicy(threadPolicy);
        }
        */
    }

    public DaggerComponent component() {
        return mComponent;
    }
}
