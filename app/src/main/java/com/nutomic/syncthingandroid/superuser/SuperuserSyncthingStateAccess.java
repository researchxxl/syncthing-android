package com.nutomic.syncthingandroid.superuser;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import com.nutomic.syncthingandroid.service.state.NormalSyncthingStateAccess;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccess;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;
import com.nutomic.syncthingandroid.service.state.SyncthingStateFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Routes fixed app-private state operations through the typed root service capabilities. */
public final class SuperuserSyncthingStateAccess implements SyncthingStateAccess {

    private static final int MAX_STATE_BYTES = 16 * 1024 * 1024;

    private final Context mContext;
    private final SuperuserClient mClient;

    public SuperuserSyncthingStateAccess(Context context, SuperuserClient client) {
        if (context == null || client == null) {
            throw new IllegalArgumentException("State access dependencies must not be null");
        }
        mContext = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        mClient = client;
    }

    @Override
    public byte[] read(SyncthingStateFile file) throws SyncthingStateAccessException {
        SuperuserStateFileResult result = mClient.openStateFileForRead(wireId(file));
        requireSuccess(result, "Failed to open Syncthing state");
        if (result.descriptor == null) {
            throw new SyncthingStateAccessException("Syncthing state file is unavailable");
        }
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(result.descriptor);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_STATE_BYTES) {
                    throw new SyncthingStateAccessException("Syncthing state file is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (IOException | SecurityException exception) {
            throw new SyncthingStateAccessException("Failed to read Syncthing state", exception);
        }
    }

    @Override
    public boolean exists(SyncthingStateFile file) throws SyncthingStateAccessException {
        SuperuserStateFileResult result = mClient.openStateFileForRead(wireId(file));
        requireSuccess(result, "Failed to inspect Syncthing state");
        if (result.descriptor == null) {
            return false;
        }
        try {
            result.descriptor.close();
            return true;
        } catch (IOException exception) {
            throw new SyncthingStateAccessException("Failed to close Syncthing state", exception);
        }
    }

    @Override
    public void writeAtomic(SyncthingStateFile file, byte[] data)
            throws SyncthingStateAccessException {
        if (data == null) {
            throw new SyncthingStateAccessException("Syncthing state data must not be null");
        }
        File transferDirectory = new File(mContext.getCacheDir(), "superuser-transfer");
        if (!transferDirectory.isDirectory()
                && !transferDirectory.mkdirs() && !transferDirectory.isDirectory()) {
            throw new SyncthingStateAccessException("Unable to create state transfer directory");
        }
        File temporary = new File(transferDirectory, UUID.randomUUID() + ".state");
        try {
            writeTransferFile(temporary, data);
            try (ParcelFileDescriptor source = ParcelFileDescriptor.open(
                    temporary, ParcelFileDescriptor.MODE_READ_ONLY)) {
                SuperuserOperationResult result = mClient.writeStateFileAtomic(
                        wireId(file), source);
                requireSuccess(result, "Failed to write Syncthing state");
            }
        } catch (IOException | SecurityException exception) {
            throw new SyncthingStateAccessException("Failed to prepare Syncthing state", exception);
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                // The file is in the app cache and contains no path or credential metadata.
            }
        }
    }

    @Override
    public void delete(SyncthingStateFile file) throws SyncthingStateAccessException {
        SuperuserOperationResult result = mClient.deleteStateFile(wireId(file));
        requireSuccess(result, "Failed to delete Syncthing state");
    }

    @Override
    public void verifyNormalAccess() throws SyncthingStateAccessException {
        new NormalSyncthingStateAccess(mContext).verifyNormalAccess();
    }

    private static int wireId(SyncthingStateFile file) throws SyncthingStateAccessException {
        if (file == null) {
            throw new SyncthingStateAccessException("Unknown Syncthing state file");
        }
        return file.wireId();
    }

    private static void requireSuccess(SuperuserOperationResult result, String message)
            throws SyncthingStateAccessException {
        if (result == null || !result.isSuccess()) {
            String diagnostic = result == null ? "" : result.diagnostic;
            throw new SyncthingStateAccessException(message + ": " + diagnostic);
        }
    }

    private static void writeTransferFile(File file, byte[] data) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            output.flush();
            output.getFD().sync();
        }
    }
}
