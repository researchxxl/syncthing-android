package com.nutomic.syncthingandroid;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.NotificationHandler;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import kotlinx.coroutines.flow.MutableStateFlow;
import me.zhanghai.compose.preference.PreferenceFlow_androidKt;
import me.zhanghai.compose.preference.Preferences;

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
    public MutableStateFlow<Preferences> preferencesMutableStateFlow(SharedPreferences sharedPreferences) {
        return PreferenceFlow_androidKt.createPreferenceFlow(sharedPreferences);
    }

    @Provides
    @Singleton
    public NotificationHandler getNotificationHandler(SharedPreferences preferences) {
        return new NotificationHandler(mApp, preferences);
    }
}
