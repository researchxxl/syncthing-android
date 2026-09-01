package com.nutomic.syncthingandroid.service.state;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Performs closed state-file operations as the normal application UID. */
public final class NormalSyncthingStateAccess implements SyncthingStateAccess {

    private final File mFilesDir;

    public NormalSyncthingStateAccess(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        mFilesDir = context.getFilesDir();
    }

    @Override
    public byte[] read(SyncthingStateFile file) throws SyncthingStateAccessException {
        File target = resolve(file);
        if (!target.isFile()) {
            throw new SyncthingStateAccessException("Syncthing state file is unavailable");
        }
        try (FileInputStream input = new FileInputStream(target);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (IOException | SecurityException exception) {
            throw new SyncthingStateAccessException("Failed to read Syncthing state", exception);
        }
    }

    @Override
    public boolean exists(SyncthingStateFile file) throws SyncthingStateAccessException {
        return resolve(file).isFile();
    }

    @Override
    public void writeAtomic(SyncthingStateFile file, byte[] data)
            throws SyncthingStateAccessException {
        if (data == null) {
            throw new SyncthingStateAccessException("Syncthing state data must not be null");
        }
        File target = resolve(file);
        File temporary = new File(mFilesDir, file.fileName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            output.write(data);
            output.flush();
            output.getFD().sync();
        } catch (IOException | SecurityException exception) {
            throw new SyncthingStateAccessException("Failed to write Syncthing state", exception);
        }
        if (!temporary.renameTo(target)) {
            throw new SyncthingStateAccessException("Failed to install Syncthing state");
        }
    }

    @Override
    public void delete(SyncthingStateFile file) throws SyncthingStateAccessException {
        File target = resolve(file);
        if (target.exists() && !target.delete()) {
            throw new SyncthingStateAccessException("Failed to delete Syncthing state");
        }
    }

    @Override
    public void verifyNormalAccess() throws SyncthingStateAccessException {
        if (!mFilesDir.isDirectory() || !mFilesDir.canRead() || !mFilesDir.canWrite()) {
            throw new SyncthingStateAccessException("App-private state is not accessible");
        }
        try {
            verifyKnownFile(new File(mFilesDir, "config.xml"));
            verifyKnownFile(new File(mFilesDir, "cert.pem"));
            verifyKnownFile(new File(mFilesDir, "key.pem"));
            verifyKnownFile(new File(mFilesDir, "https-cert.pem"));
            verifyKnownFile(new File(mFilesDir, "https-key.pem"));
            verifyTreeIfPresent(new File(mFilesDir, "index-v2"));

            File probe = new File(mFilesDir, ".syncthing-normal-access");
            if (probe.exists() || isSymbolicLink(probe) || !probe.createNewFile()) {
                throw new IOException("Normal app access probe could not be created");
            }
            try (FileOutputStream output = new FileOutputStream(probe)) {
                output.write(0);
                output.flush();
                output.getFD().sync();
            }
            if (!probe.delete()) {
                throw new IOException("Normal app access probe could not be removed");
            }
        } catch (IOException | SecurityException exception) {
            throw new SyncthingStateAccessException(
                    "App-private state is not accessible to the normal UID", exception);
        }
    }

    private File resolve(SyncthingStateFile file) throws SyncthingStateAccessException {
        if (file == null) {
            throw new SyncthingStateAccessException("Unknown Syncthing state file");
        }
        File target = file.resolve(mFilesDir);
        try {
            if (isSymbolicLink(target)) {
                throw new SyncthingStateAccessException(
                        "Symbolic-link Syncthing state is not supported");
            }
        } catch (IOException exception) {
            throw new SyncthingStateAccessException(
                    "Unable to inspect Syncthing state", exception);
        }
        return target;
    }

    private static void verifyKnownFile(File file) throws IOException {
        if (isSymbolicLink(file)) {
            throw new IOException("Symbolic-link app-private state is not supported");
        }
        if (file.exists()) {
            verifyRegularFile(file);
        }
    }

    private static void verifyTreeIfPresent(File file) throws IOException {
        if (isSymbolicLink(file)) {
            throw new IOException("Symbolic-link app-private state is not supported");
        }
        if (!file.exists()) {
            return;
        }
        verifyTree(file);
    }

    private static void verifyTree(File file) throws IOException {
        if (isSymbolicLink(file)) {
            throw new IOException("Symbolic-link app-private state is not supported");
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Unable to inspect app-private state");
            }
            for (File child : children) {
                verifyTree(child);
            }
        } else {
            verifyRegularFile(file);
        }
    }

    private static void verifyRegularFile(File file) throws IOException {
        if (!file.isFile()) {
            throw new IOException("App-private state entry is not a regular file");
        }
        try (FileInputStream input = new FileInputStream(file)) {
            input.read();
        }
    }

    private static boolean isSymbolicLink(File file) throws IOException {
        try {
            return (Os.lstat(file.getPath()).st_mode & OsConstants.S_IFMT)
                    == OsConstants.S_IFLNK;
        } catch (ErrnoException exception) {
            if (exception.errno == android.system.OsConstants.ENOENT) {
                return false;
            }
            throw new IOException("Unable to inspect app-private state", exception);
        }
    }
}
