package com.nutomic.syncthingandroid.service.state;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/** Copies the fixed Syncthing backup snapshot as the normal application UID. */
public final class NormalSyncthingStateTransfer implements SyncthingStateTransfer {

    /** Fixed cache subdirectory shared by normal and privileged transfer implementations. */
    public static final String STAGING_DIRECTORY = "superuser-state";
    private static final String[] SNAPSHOT_NAMES = {
            Constants.CONFIG_FILE,
            Constants.PUBLIC_KEY_FILE,
            Constants.PRIVATE_KEY_FILE,
            Constants.HTTPS_CERT_FILE,
            Constants.HTTPS_KEY_FILE,
            "index-v2"
    };

    private final File mFilesDir;
    private final File mStagingBase;

    public NormalSyncthingStateTransfer(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        mFilesDir = appContext.getFilesDir();
        mStagingBase = stagingBase(appContext);
    }

    @Override
    public File stageBackupState() throws SyncthingStateAccessException {
        File staging = null;
        boolean createdByThisInvocation = false;
        try {
            ensureStagingBase(mStagingBase.getParentFile(), mStagingBase);
            staging = new File(mStagingBase, UUID.randomUUID().toString());
            if (isSymbolicLink(staging) || !staging.mkdir()) {
                throw new IOException("Unable to create state staging entry");
            }
            createdByThisInvocation = true;
            for (String name : SNAPSHOT_NAMES) {
                File source = new File(mFilesDir, name);
                if (isSymbolicLink(source)) {
                    throw new IOException("State source symlinks are not allowed");
                }
                if (source.exists()) {
                    copyTree(source, new File(staging, name), mFilesDir);
                }
            }
            ensureSafeTree(staging, mStagingBase);
            return staging;
        } catch (IOException | SecurityException exception) {
            if (createdByThisInvocation) {
                deleteStagingQuietly(mStagingBase, staging);
            }
            throw new SyncthingStateAccessException("Failed to stage Syncthing state", exception);
        }
    }

    private static void deleteStagingQuietly(File base, File staging) {
        if (base == null || staging == null) {
            return;
        }
        try {
            validateStagingDirectory(base, staging);
            deleteTree(staging);
        } catch (IOException | SyncthingStateAccessException | RuntimeException ignored) {
            // The caller receives the operation failure; an unsafe base is never followed.
        }
    }

    @Override
    public void installBackupState(File stagedState) throws SyncthingStateAccessException {
        validateStagingDirectory(stagedState);
        try {
            for (File child : stagedState.listFiles()) {
                if (child == null || !isSnapshotName(child.getName())) {
                    throw new IOException("Staging directory contains an unsupported entry");
                }
                ensureSafeTree(child, stagedState);
                File target = new File(mFilesDir, child.getName());
                if (child.isDirectory()) {
                    deleteTree(target);
                    copyTree(child, target, stagedState);
                } else {
                    copyFileAtomic(child, target);
                }
            }
        } catch (IOException | SecurityException exception) {
            throw new SyncthingStateAccessException("Failed to install Syncthing state", exception);
        }
    }

    File stagingBase() {
        return mStagingBase;
    }

    /** Returns the fixed staging base for an application context. */
    public static File stagingBase(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        return new File(appContext.getCacheDir(), STAGING_DIRECTORY);
    }

    /**
     * Creates or validates the fixed staging base without following a symlink at either level.
     * The privileged service uses this before accepting any caller-supplied transfer identifier.
     */
    public static void ensureStagingBase(File cacheDir, File stagingBase) throws IOException {
        if (cacheDir == null || stagingBase == null) {
            throw new IOException("State staging location is missing");
        }
        if (isSymbolicLink(cacheDir) || isSymbolicLink(stagingBase)) {
            throw new IOException("State staging symlinks are not allowed");
        }
        File canonicalCacheDir = cacheDir.getCanonicalFile();
        if (!canonicalCacheDir.equals(stagingBase.getCanonicalFile().getParentFile())) {
            throw new IOException("State staging base is outside the fixed cache location");
        }
        if (!cacheDir.isDirectory()) {
            throw new IOException("State cache directory is unavailable");
        }
        if (!stagingBase.exists() && !stagingBase.mkdir()) {
            throw new IOException("Unable to create state staging base");
        }
        if (isSymbolicLink(stagingBase) || !stagingBase.isDirectory()) {
            throw new IOException("State staging base is not a directory");
        }
        if (!stagingBase.getCanonicalFile().getParentFile().equals(cacheDir.getCanonicalFile())) {
            throw new IOException("State staging base is outside the fixed cache location");
        }
    }

    /** Accepts only canonical UUID transfer identifiers. */
    public static boolean isValidTransferId(String transferId) {
        if (transferId == null || transferId.isEmpty()) {
            return false;
        }
        try {
            return UUID.fromString(transferId).toString().equals(transferId);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Returns whether a top-level entry belongs to the fixed backup snapshot. */
    public static boolean isSnapshotName(String name) {
        for (String snapshotName : SNAPSHOT_NAMES) {
            if (snapshotName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Returns a copy of the fixed top-level snapshot names. */
    public static String[] snapshotNames() {
        return SNAPSHOT_NAMES.clone();
    }

    /** Rejects any unsupported entry at the top level of a completed state snapshot. */
    public static void validateSnapshotEntries(File stagedState) throws IOException {
        if (stagedState == null || !stagedState.isDirectory()) {
            throw new IOException("State staging directory is unavailable");
        }
        File[] entries = stagedState.listFiles();
        if (entries == null) {
            throw new IOException("Unable to inspect state staging directory");
        }
        for (File entry : entries) {
            if (entry == null || !isSnapshotName(entry.getName())) {
                throw new IOException("State staging directory contains an unsupported entry");
            }
        }
    }

    private static void validateStagingDirectoryShape(File stagedState)
            throws SyncthingStateAccessException {
        if (stagedState == null || !isValidTransferId(stagedState.getName())) {
            throw new SyncthingStateAccessException("Invalid Syncthing state staging directory");
        }
    }

    private void validateStagingDirectory(File stagedState)
            throws SyncthingStateAccessException {
        validateStagingDirectory(mStagingBase, stagedState);
    }

    /**
     * Validates a transfer directory against the fixed base and UUID-only directory rule.
     * Callers must run this before reading or writing any transfer entry.
     */
    public static void validateStagingDirectory(File stagingBase, File stagedState)
            throws SyncthingStateAccessException {
        validateStagingDirectoryShape(stagedState);
        if (stagingBase == null) {
            throw new SyncthingStateAccessException("State staging base is missing");
        }
        try {
            if (isSymbolicLink(stagingBase) || isSymbolicLink(stagedState)) {
                throw new SyncthingStateAccessException(
                        "State staging symlinks are not allowed");
            }
            if (!stagingBase.isDirectory() || !stagedState.isDirectory()) {
                throw new SyncthingStateAccessException(
                        "Invalid Syncthing state staging directory");
            }
            if (!stagedState.getCanonicalFile().getParentFile().equals(
                    stagingBase.getCanonicalFile())) {
                throw new SyncthingStateAccessException(
                        "Syncthing state staging directory is outside the fixed base");
            }
        } catch (IOException exception) {
            throw new SyncthingStateAccessException("Unable to validate state staging directory",
                    exception);
        }
    }

    /** Rejects symlinks and entries escaping a transfer root. */
    public static void ensureSafeTree(File file, File root) throws IOException {
        if (file == null || root == null || isSymbolicLink(root)) {
            throw new IOException("State staging symlinks are not allowed");
        }
        ensureSafeTreeInternal(file, root);
    }

    private static void ensureSafeTreeInternal(File file, File root) throws IOException {
        String canonicalFile = file.getCanonicalPath();
        String canonicalRoot = root.getCanonicalPath();
        if (!canonicalFile.equals(canonicalRoot)
                && !canonicalFile.startsWith(canonicalRoot + File.separator)) {
            throw new IOException("State staging entry escapes its root");
        }
        if (isSymbolicLink(file)) {
            throw new IOException("State staging symlinks are not allowed");
        }
        if (!file.exists()) {
            throw new IOException("State staging entry does not exist");
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Unable to inspect state staging entry");
            }
            for (File child : children) {
                ensureSafeTreeInternal(child, root);
            }
        }
    }

    /** Copies a file or directory while keeping the source confined to sourceRoot. */
    public static void copyTree(File source, File target, File sourceRoot) throws IOException {
        String canonicalSource = source.getCanonicalPath();
        String canonicalRoot = sourceRoot.getCanonicalPath();
        if (isSymbolicLink(sourceRoot)
                || (!canonicalSource.equals(canonicalRoot)
                && !canonicalSource.startsWith(canonicalRoot + File.separator))) {
            throw new IOException("State source escapes app-private storage");
        }
        if (isSymbolicLink(source)) {
            throw new IOException("State source symlinks are not allowed");
        }
        if (source.isDirectory()) {
            if (isSymbolicLink(target)) {
                throw new IOException("State staging symlinks are not allowed");
            }
            if (!target.mkdir() && !target.isDirectory()) {
                throw new IOException("Unable to create state directory");
            }
            File[] children = source.listFiles();
            if (children == null) {
                throw new IOException("Unable to inspect state directory");
            }
            for (File child : children) {
                copyTree(child, new File(target, child.getName()), sourceRoot);
            }
            return;
        }
        copyFile(source, target);
    }

    /** Installs a regular state file through a same-directory temporary file. */
    public static void copyFileAtomic(File source, File target) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".transfer.tmp");
        copyFile(source, temporary);
        if (!temporary.renameTo(target)) {
            temporary.delete();
            throw new IOException("Unable to install state file");
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        if (isSymbolicLink(target)) {
            throw new IOException("State staging symlinks are not allowed");
        }
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
        }
    }

    /** Removes a fixed transfer subtree without following symlinks. */
    public static void deleteTree(File file) throws IOException {
        if (isSymbolicLink(file)) {
            if (!file.delete()) {
                throw new IOException("Unable to remove state staging symlink");
            }
            return;
        }
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Unable to inspect state directory");
            }
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("Unable to remove previous state");
        }
    }

    /** Returns whether the exact path is a symbolic link, without resolving its target. */
    public static boolean isSymbolicLink(File file) throws IOException {
        if (file == null) {
            return false;
        }
        // Robolectric's Os.lstat implementation may resolve a link target. Compare only the
        // final path component here so a platform link such as /var -> /private/var is not
        // mistaken for a link at the file being checked. This also detects dangling links.
        File absoluteFile = file.getAbsoluteFile();
        File absoluteParent = absoluteFile.getParentFile();
        if (absoluteParent != null) {
            File canonicalFile = file.getCanonicalFile();
            File canonicalParent = absoluteParent.getCanonicalFile();
            if (!canonicalParent.equals(canonicalFile.getParentFile())
                    || !absoluteFile.getName().equals(canonicalFile.getName())) {
                return true;
            }
        }
        if (!file.exists()) {
            return false;
        }
        try {
            return (Os.lstat(file.getPath()).st_mode & OsConstants.S_IFMT)
                    == OsConstants.S_IFLNK;
        } catch (ErrnoException exception) {
            if (exception.errno == OsConstants.ENOENT) {
                return false;
            }
            throw new IOException("Unable to inspect state entry", exception);
        }
    }
}
