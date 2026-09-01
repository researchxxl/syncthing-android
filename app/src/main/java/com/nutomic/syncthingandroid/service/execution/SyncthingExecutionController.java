package com.nutomic.syncthingandroid.service.execution;

import android.content.SharedPreferences;

import com.nutomic.syncthingandroid.service.AppPrefs;

import java.util.Map;

/** Selects the configured execution backend without falling back between privilege modes. */
public class SyncthingExecutionController implements SyncthingExecutionBackend {

    private final SharedPreferences mPreferences;
    private final SyncthingExecutionBackend mNormalBackend;
    private final SyncthingExecutionBackend mSuperuserBackend;

    public SyncthingExecutionController(SharedPreferences preferences,
                                        SyncthingExecutionBackend normalBackend) {
        this(preferences, normalBackend, null);
    }

    public SyncthingExecutionController(SharedPreferences preferences,
                                        SyncthingExecutionBackend normalBackend,
                                        SyncthingExecutionBackend superuserBackend) {
        mPreferences = preferences;
        mNormalBackend = normalBackend;
        mSuperuserBackend = superuserBackend;
    }

    @Override
    public SyncthingExecutionResult execute(SyncthingCommand command,
                                            Map<String, String> environment,
                                            boolean captureStdout)
            throws SyncthingExecutionException, InterruptedException {
        return selectedBackend().execute(command, environment, captureStdout);
    }

    @Override
    public void stopOwnedProcess() throws SyncthingExecutionException {
        selectedBackend().stopOwnedProcess();
    }

    private SyncthingExecutionBackend selectedBackend() throws SyncthingExecutionException {
        if (!AppPrefs.getUseRoot(mPreferences)) {
            return mNormalBackend;
        }
        if (mSuperuserBackend == null) {
            throw new SyncthingExecutionException(
                    "Superuser mode is configured but no privileged backend is available");
        }
        return mSuperuserBackend;
    }
}
