package com.nutomic.syncthingandroid.service.folder;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Performs folder probes and conflict discovery using Java filesystem APIs as the app UID. */
public final class NormalSyncthingFolderAccess implements SyncthingFolderAccess {

    private static final String WRITE_PROBE_NAME = ".stwritetest";
    private static final Pattern CONFLICT_FILE = Pattern.compile(
            ".*\\.sync-conflict-\\d{8}-\\d{6}-[A-Za-z0-9]{8}.*");

    private final File mConfigFile;

    public NormalSyncthingFolderAccess(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        mConfigFile = Constants.getConfigFile(appContext);
    }

    @Override
    public boolean canWrite(String absoluteFolderPath) throws SyncthingFolderAccessException {
        File folder = requireAbsolutePath(absoluteFolderPath);
        if (!folder.isDirectory()) {
            return false;
        }
        File probe = new File(folder, WRITE_PROBE_NAME);
        try {
            if (probe.exists()) {
                return false;
            }
            if (!probe.createNewFile()) {
                return false;
            }
            try (FileOutputStream output = new FileOutputStream(probe)) {
                output.flush();
            }
            return probe.delete();
        } catch (IOException | SecurityException exception) {
            return false;
        } finally {
            if (probe.exists() && !probe.delete()) {
                // Do not turn a successful access probe into an arbitrary cleanup operation.
            }
        }
    }

    @Override
    public String[] findSyncConflicts(String absoluteConfiguredFolderPath)
            throws SyncthingFolderAccessException {
        File folder = requireAbsolutePath(absoluteConfiguredFolderPath);
        try {
            if (!ConfiguredFolderPathMatcher.contains(mConfigFile, folder.getPath())) {
                throw new SyncthingFolderAccessException(
                        "Folder is not present in Syncthing configuration");
            }
        } catch (IOException exception) {
            throw new SyncthingFolderAccessException(
                    "Unable to validate configured Syncthing folder", exception);
        }
        if (!folder.isDirectory()) {
            return new String[0];
        }

        List<String> conflicts = new ArrayList<>();
        ArrayDeque<File> pending = new ArrayDeque<>();
        pending.add(folder);
        while (!pending.isEmpty()) {
            File current = pending.removeFirst();
            File[] entries = current.listFiles();
            if (entries == null) {
                throw new SyncthingFolderAccessException("Unable to inspect Syncthing folder");
            }
            for (File entry : entries) {
                try {
                    if (isSymbolicLink(entry)) {
                        continue;
                    }
                } catch (IOException exception) {
                    throw new SyncthingFolderAccessException(
                            "Unable to inspect Syncthing folder entry", exception);
                }
                if (entry.isDirectory()) {
                    if (!Constants.FOLDER_NAME_STVERSIONS.equals(entry.getName())) {
                        pending.addLast(entry);
                    }
                } else if (entry.isFile() && CONFLICT_FILE.matcher(entry.getName()).matches()) {
                    conflicts.add(relativePath(folder, entry));
                }
            }
        }
        Collections.sort(conflicts);
        return conflicts.toArray(new String[0]);
    }

    private static File requireAbsolutePath(String path) throws SyncthingFolderAccessException {
        if (path == null || path.isEmpty() || !new File(path).isAbsolute()) {
            throw new SyncthingFolderAccessException("Folder path must be absolute");
        }
        return new File(path);
    }

    private static String relativePath(File root, File child) {
        String rootPath = root.getAbsolutePath();
        String childPath = child.getAbsolutePath();
        return childPath.startsWith(rootPath + File.separator)
                ? childPath.substring(rootPath.length() + 1)
                : childPath;
    }

    private static boolean isSymbolicLink(File file) throws IOException {
        try {
            return (Os.lstat(file.getPath()).st_mode & OsConstants.S_IFMT)
                    == OsConstants.S_IFLNK;
        } catch (ErrnoException exception) {
            throw new IOException("Unable to inspect folder entry", exception);
        }
    }
}
