package com.nutomic.syncthingandroid;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.runtime.AppUidBackend;
import com.nutomic.syncthingandroid.runtime.DefaultSyncthingRuntime;
import com.nutomic.syncthingandroid.service.NotificationHandler;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class SyncthingModule {

    private final SyncthingApp mApp;

    public SyncthingModule(SyncthingApp app) {
        mApp = app;
    }

    @Provides
    @Singleton
    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mApp);
    }

    @Provides
    @Singleton
    public NotificationHandler getNotificationHandler(SharedPreferences preferences) {
        return new NotificationHandler(mApp, preferences);
    }

    @Provides
    @Singleton
    public DefaultSyncthingRuntime getSyncthingRuntime() {
        return new DefaultSyncthingRuntime(new AppUidBackend(mApp));
    }
}
