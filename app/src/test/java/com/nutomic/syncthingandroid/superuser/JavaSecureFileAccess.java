package com.nutomic.syncthingandroid.superuser;

import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** JVM-only secure-file implementation for exercising record behavior on the local filesystem. */
final class JavaSecureFileAccess implements SecureFileAccess {

    @Override
    public OpenedFile openExistingRegular(File file, int accessFlags) throws IOException {
        Path path = requireRegularPath(file);
        RandomAccessFile randomAccessFile = new RandomAccessFile(path.toFile(), "rw");
        return new JavaOpenedFile(randomAccessFile);
    }

    @Override
    public OpenedFile createNewRegular(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        try {
            Files.createFile(file.toPath());
        } catch (FileAlreadyExistsException exception) {
            throw new SecureFileAccess.AccessException(OsConstants.EEXIST,
                    "Secure file already exists", exception);
        }
        try {
            return new JavaOpenedFile(new RandomAccessFile(file, "rw"));
        } catch (IOException exception) {
            Files.deleteIfExists(file.toPath());
            throw exception;
        }
    }

    private static Path requireRegularPath(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(file.toPath(),
                    BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                throw new IOException("Secure file is not a regular file");
            }
            return file.toPath();
        } catch (NoSuchFileException exception) {
            throw new SecureFileAccess.AccessException(OsConstants.ENOENT,
                    "Secure file is missing", exception);
        }
    }

    private static final class JavaOpenedFile implements OpenedFile {
        private final RandomAccessFile mFile;

        JavaOpenedFile(RandomAccessFile file) {
            mFile = file;
        }

        @Override
        public FileDescriptor descriptor() {
            try {
                return mFile.getFD();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to get test file descriptor", exception);
            }
        }

        @Override
        public void truncate(long length) throws IOException {
            mFile.setLength(length);
        }

        @Override
        public void sync() throws IOException {
            mFile.getFD().sync();
        }

        @Override
        public void close() throws IOException {
            mFile.close();
        }
    }
}
