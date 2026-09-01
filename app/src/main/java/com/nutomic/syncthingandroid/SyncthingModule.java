package com.nutomic.syncthingandroid;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import com.nutomic.syncthingandroid.service.NotificationHandler;
import com.nutomic.syncthingandroid.service.Constants;
import com.nutomic.syncthingandroid.service.execution.NormalSyncthingExecutionBackend;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionBackend;
import com.nutomic.syncthingandroid.service.execution.SyncthingExecutionController;
import com.nutomic.syncthingandroid.service.folder.NormalSyncthingFolderAccess;
import com.nutomic.syncthingandroid.service.folder.SyncthingFolderAccess;
import com.nutomic.syncthingandroid.service.folder.SyncthingFolderAccessRouter;
import com.nutomic.syncthingandroid.superuser.SuperuserClient;
import com.nutomic.syncthingandroid.superuser.SuperuserSyncthingFolderAccess;
import com.nutomic.syncthingandroid.superuser.SuperuserModeController;
import com.nutomic.syncthingandroid.superuser.SuperuserSyncthingExecutionBackend;
import com.nutomic.syncthingandroid.superuser.SuperuserSyncthingStateAccess;
import com.nutomic.syncthingandroid.superuser.SuperuserSyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateAccess;
import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccess;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessRouter;
import com.nutomic.syncthingandroid.service.state.SyncthingStateTransfer;
import com.nutomic.syncthingandroid.service.state.SyncthingStateTransferRouter;

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
    public SuperuserClient getSuperuserClient() {
        return new SuperuserClient(mApp);
    }

    @Provides
    @Singleton
    public SuperuserSyncthingExecutionBackend getSuperuserSyncthingExecutionBackend(
            SuperuserClient client) {
        return new SuperuserSyncthingExecutionBackend(client);
    }

    @Provides
    @Singleton
    public NormalSyncthingStateAccess getNormalSyncthingStateAccess() {
        return new NormalSyncthingStateAccess(mApp);
    }

    @Provides
    @Singleton
    public SuperuserSyncthingStateAccess getSuperuserSyncthingStateAccess(
            SuperuserClient client) {
        return new SuperuserSyncthingStateAccess(mApp, client);
    }

    @Provides
    @Singleton
    public NormalSyncthingStateTransfer getNormalSyncthingStateTransfer() {
        return new NormalSyncthingStateTransfer(mApp);
    }

    @Provides
    @Singleton
    public SuperuserSyncthingStateTransfer getSuperuserSyncthingStateTransfer(
            SuperuserClient client) {
        return new SuperuserSyncthingStateTransfer(mApp, client);
    }

    @Provides
    @Singleton
    public SyncthingStateTransferRouter getSyncthingStateTransferRouter(
            SharedPreferences preferences, NormalSyncthingStateTransfer normalTransfer,
            SuperuserSyncthingStateTransfer superuserTransfer) {
        SyncthingStateTransfer normal = normalTransfer;
        SyncthingStateTransfer root = superuserTransfer;
        return new SyncthingStateTransferRouter(preferences, normal, root);
    }

    @Provides
    @Singleton
    public SyncthingStateTransfer getSyncthingStateTransfer(
            SyncthingStateTransferRouter router) {
        return router;
    }

    @Provides
    @Singleton
    public NormalSyncthingFolderAccess getNormalSyncthingFolderAccess() {
        return new NormalSyncthingFolderAccess(mApp);
    }

    @Provides
    @Singleton
    public SuperuserSyncthingFolderAccess getSuperuserSyncthingFolderAccess(
            SuperuserClient client) {
        return new SuperuserSyncthingFolderAccess(mApp, client);
    }

    @Provides
    @Singleton
    public SyncthingFolderAccessRouter getSyncthingFolderAccessRouter(
            SharedPreferences preferences, NormalSyncthingFolderAccess normalAccess,
            SuperuserSyncthingFolderAccess superuserAccess) {
        SyncthingFolderAccess normal = normalAccess;
        SyncthingFolderAccess root = superuserAccess;
        return new SyncthingFolderAccessRouter(preferences, normal, root);
    }

    @Provides
    @Singleton
    public SyncthingFolderAccess getSyncthingFolderAccess(
            SyncthingFolderAccessRouter router) {
        return router;
    }

    @Provides
    @Singleton
    public SuperuserModeController getSuperuserModeController(
            SharedPreferences preferences, SuperuserClient client,
            NormalSyncthingStateAccess normalStateAccess) {
        return new SuperuserModeController(mApp, preferences, client, normalStateAccess);
    }

    @Provides
    @Singleton
    public SyncthingStateAccessRouter getSyncthingStateAccessRouter(
            SharedPreferences preferences, NormalSyncthingStateAccess normalAccess,
            SuperuserSyncthingStateAccess superuserAccess) {
        SyncthingStateAccess normal = normalAccess;
        SyncthingStateAccess root = superuserAccess;
        return new SyncthingStateAccessRouter(preferences, normal, root);
    }

    @Provides
    @Singleton
    public SyncthingStateAccess getSyncthingStateAccess(SyncthingStateAccessRouter router) {
        return router;
    }

    @Provides
    @Singleton
    public NormalSyncthingExecutionBackend getNormalSyncthingExecutionBackend() {
        return new NormalSyncthingExecutionBackend(
                Constants.getSyncthingBinary(mApp), Constants.getSyncthingLogFile(mApp));
    }

    @Provides
    @Singleton
    public SyncthingExecutionController getSyncthingExecutionController(
            SharedPreferences preferences, NormalSyncthingExecutionBackend normalBackend,
            SuperuserSyncthingExecutionBackend superuserBackend) {
        SyncthingExecutionBackend normalExecutionBackend = normalBackend;
        SyncthingExecutionBackend rootExecutionBackend = superuserBackend;
        return new SyncthingExecutionController(
                preferences, normalExecutionBackend, rootExecutionBackend);
    }
}
