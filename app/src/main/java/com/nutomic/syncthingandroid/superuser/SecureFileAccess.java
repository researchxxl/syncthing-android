package com.nutomic.syncthingandroid.superuser;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Opens files used by the privileged boundary without following the final path component.
 *
 * <p>The path checks in {@link File} are not sufficient when the caller runs as UID 0: a
 * fixed-name symlink in an app-owned directory could redirect a subsequent open. The Android
 * implementation applies {@code O_NOFOLLOW}, then validates the type of the descriptor returned
 * by the kernel before handing it to Java code.</p>
 */
interface SecureFileAccess {

    /** Returns the descriptor-backed implementation used by Android processes. */
    static SecureFileAccess production() {
        return new AndroidSecureFileAccess();
    }

    OpenedFile openExistingRegular(File file, int accessFlags) throws IOException;

    OpenedFile createNewRegular(File file) throws IOException;

    /** An already-validated descriptor with operations needed by the owning caller. */
    interface OpenedFile extends AutoCloseable {
        FileDescriptor descriptor();

        void truncate(long length) throws IOException;

        void sync() throws IOException;

        /** Duplicates the descriptor for returning it across the Binder boundary. */
        default ParcelFileDescriptor duplicate() throws IOException {
            throw new UnsupportedOperationException("Descriptor duplication is not available");
        }

        @Override
        void close() throws IOException;
    }

    /** Carries the kernel errno so callers can distinguish a missing optional file. */
    final class AccessException extends IOException {
        private final int mErrno;

        AccessException(int errno, String message, Throwable cause) {
            super(message, cause);
            mErrno = errno;
        }

        int errno() {
            return mErrno;
        }
    }
}

/** Android implementation backed by kernel descriptors and no-follow open flags. */
final class AndroidSecureFileAccess implements SecureFileAccess {

    @Override
    public SecureFileAccess.OpenedFile openExistingRegular(File file, int accessFlags)
            throws IOException {
        return open(file, accessFlags | OsConstants.O_NOFOLLOW, 0);
    }

    @Override
    public SecureFileAccess.OpenedFile createNewRegular(File file) throws IOException {
        return open(file, OsConstants.O_RDWR | OsConstants.O_CREAT | OsConstants.O_EXCL
                | OsConstants.O_NOFOLLOW, 0600);
    }

    private static SecureFileAccess.OpenedFile open(File file, int flags, int mode)
            throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }

        FileDescriptor descriptor = null;
        try {
            descriptor = Os.open(file.getPath(), flags, mode);
            if (descriptor == null) {
                throw new IOException("Android returned no secure file descriptor");
            }
            StructStat stat = Os.fstat(descriptor);
            if (!descriptor.valid()
                    || (stat.st_mode & OsConstants.S_IFMT) != OsConstants.S_IFREG) {
                throw new IOException("Secure file descriptor is not a regular file");
            }
            SecureFileAccess.OpenedFile result = new AndroidOpenedFile(descriptor);
            descriptor = null;
            return result;
        } catch (ErrnoException exception) {
            throw new SecureFileAccess.AccessException(
                    exception.errno, "Unable to open secure file", exception);
        } finally {
            if (descriptor != null) {
                try {
                    Os.close(descriptor);
                } catch (ErrnoException ignored) {
                    // Preserve the original open or validation failure.
                }
            }
        }
    }

    private static final class AndroidOpenedFile implements SecureFileAccess.OpenedFile {
        private final FileDescriptor mDescriptor;
        private boolean mClosed;

        AndroidOpenedFile(FileDescriptor descriptor) {
            mDescriptor = descriptor;
        }

        @Override
        public FileDescriptor descriptor() {
            return mDescriptor;
        }

        @Override
        public void truncate(long length) throws IOException {
            try {
                Os.ftruncate(mDescriptor, length);
            } catch (ErrnoException exception) {
                throw new IOException("Unable to truncate secure file", exception);
            }
        }

        @Override
        public void sync() throws IOException {
            try {
                Os.fsync(mDescriptor);
            } catch (ErrnoException exception) {
                throw new IOException("Unable to sync secure file", exception);
            }
        }

        @Override
        public ParcelFileDescriptor duplicate() throws IOException {
            return ParcelFileDescriptor.dup(mDescriptor);
        }

        @Override
        public void close() throws IOException {
            if (mClosed) {
                return;
            }
            mClosed = true;
            try {
                Os.close(mDescriptor);
            } catch (ErrnoException exception) {
                throw new IOException("Unable to close secure file", exception);
            }
        }
    }
}
