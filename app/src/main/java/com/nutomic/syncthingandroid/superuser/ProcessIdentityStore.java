package com.nutomic.syncthingandroid.superuser;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Persists root-process identity without replacing the app-owned record inode.
 *
 * <p>The normal process creates this file before it binds the RootService. The privileged
 * process only updates the existing inode, so root ownership is never introduced by a rename or
 * temporary-file replacement. A damaged record is deliberately reported as {@code CORRUPT}.</p>
 */
public final class ProcessIdentityStore {

    public static final String RECORD_FILE_NAME = "syncthing-superuser-process-identity";

    private static final String FORMAT_VERSION = "1";
    private static final int MAX_RECORD_BYTES = 16 * 1024;

    private final File mRecordFile;

    public ProcessIdentityStore(Context context) {
        this(new File(context.getNoBackupFilesDir(), RECORD_FILE_NAME));
    }

    ProcessIdentityStore(File recordFile) {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordFile must not be null");
        }
        mRecordFile = recordFile;
    }

    /** Returns the current record; a missing file is the only implicit clear state. */
    public Snapshot read() {
        if (!mRecordFile.exists()) {
            return Snapshot.clear();
        }
        if (!mRecordFile.isFile()) {
            return Snapshot.corrupt();
        }

        try {
            String content = readUtf8(mRecordFile);
            String[] fields = content.split("\\n", -1);
            if (fields.length != 6) {
                return Snapshot.corrupt();
            }
            String payload = fields[0] + "\n" + fields[1] + "\n" + fields[2] + "\n"
                    + fields[3] + "\n" + fields[4] + "\n";
            if (!constantTimeEquals(fields[5], sha256Hex(payload))) {
                return Snapshot.corrupt();
            }
            if (!FORMAT_VERSION.equals(fields[0])) {
                return Snapshot.corrupt();
            }

            ProcessIdentityRecordState state = ProcessIdentityRecordState.valueOf(fields[1]);
            int pid = Integer.parseInt(fields[2]);
            long startTimeTicks = Long.parseLong(fields[3]);
            String executable = decodeHex(fields[4]);
            if (state == ProcessIdentityRecordState.CLEAR
                    || state == ProcessIdentityRecordState.LAUNCH_PENDING) {
                if (pid != -1 || startTimeTicks != 0 || !executable.isEmpty()) {
                    return Snapshot.corrupt();
                }
                return new Snapshot(state, null);
            }
            if (state != ProcessIdentityRecordState.RUNNING) {
                return Snapshot.corrupt();
            }
            return new Snapshot(state, new ProcessIdentity(pid, startTimeTicks, executable));
        } catch (IOException | IllegalArgumentException | SecurityException exception) {
            return Snapshot.corrupt();
        }
    }

    /** Creates the record as the normal app UID if it does not already exist. */
    public boolean ensureExists() {
        try {
            File parent = mRecordFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                return false;
            }
            if (!mRecordFile.exists()) {
                if (!mRecordFile.createNewFile()) {
                    return false;
                }
                return writeRecord(ProcessIdentityRecordState.CLEAR, null);
            }
            return mRecordFile.isFile();
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    /** Writes a valid clear marker into the existing inode. */
    public boolean clear() {
        return writeRecord(ProcessIdentityRecordState.CLEAR, null);
    }

    /** Writes the pre-launch marker into the existing inode. */
    public boolean writeLaunchPending() {
        return writeRecord(ProcessIdentityRecordState.LAUNCH_PENDING, null);
    }

    /** Writes a fully verified running identity into the existing inode. */
    public boolean writeRunning(ProcessIdentity identity) {
        return identity != null && writeRecord(ProcessIdentityRecordState.RUNNING, identity);
    }

    /** The parsed record, including its durable state and optional identity. */
    public record Snapshot(ProcessIdentityRecordState state, ProcessIdentity identity) {
        public Snapshot {
            if (state == null) {
                throw new IllegalArgumentException("state must not be null");
            }
            if ((state == ProcessIdentityRecordState.RUNNING) != (identity != null)) {
                throw new IllegalArgumentException("running records require an identity");
            }
        }

        static Snapshot clear() {
            return new Snapshot(ProcessIdentityRecordState.CLEAR, null);
        }

        static Snapshot corrupt() {
            return new Snapshot(ProcessIdentityRecordState.CORRUPT, null);
        }
    }

    /** Parsed fields from Linux {@code /proc/<pid>/stat}. */
    public record ProcStat(int parentPid, long startTimeTicks) {
    }

    /** Parses PPID and start time after the final closing parenthesis of field 2. */
    public static ProcStat parseProcStat(String stat) {
        if (stat == null) {
            throw new IllegalArgumentException("stat must not be null");
        }
        int closingName = stat.lastIndexOf(')');
        if (closingName < 0 || closingName + 1 >= stat.length()) {
            throw new IllegalArgumentException("Malformed /proc stat");
        }
        String[] fields = stat.substring(closingName + 1).trim().split("\\s+");
        if (fields.length <= 19) {
            throw new IllegalArgumentException("Incomplete /proc stat");
        }
        try {
            int parentPid = Integer.parseInt(fields[1]);
            long startTimeTicks = Long.parseLong(fields[19]);
            if (parentPid < 0 || startTimeTicks <= 0) {
                throw new IllegalArgumentException("Invalid /proc stat identity");
            }
            return new ProcStat(parentPid, startTimeTicks);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid /proc stat number", exception);
        }
    }

    private boolean writeRecord(ProcessIdentityRecordState state, ProcessIdentity identity) {
        if (!mRecordFile.exists() || !mRecordFile.isFile()) {
            return false;
        }
        int pid = identity == null ? -1 : identity.pid();
        long startTimeTicks = identity == null ? 0 : identity.startTimeTicks();
        String executable = identity == null ? "" : encodeHex(identity.executable());
        String payload = FORMAT_VERSION + "\n" + state.name() + "\n" + pid + "\n"
                + startTimeTicks + "\n" + executable + "\n";
        String content = payload + sha256Hex(payload);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (RandomAccessFile output = new RandomAccessFile(mRecordFile, "rw")) {
            output.setLength(0);
            output.write(bytes);
            output.getFD().sync();
            return true;
        } catch (IOException | SecurityException exception) {
            return false;
        }
    }

    private static String readUtf8(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_RECORD_BYTES) {
                    throw new IOException("Identity record is too large");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String encodeHex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte valueByte : bytes) {
            output.append(String.format("%02x", valueByte & 0xff));
        }
        return output.toString();
    }

    private static String decodeHex(String value) {
        if ((value.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid executable encoding");
        }
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid executable encoding");
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(digest.length * 2);
            for (byte valueByte : digest) {
                output.append(String.format("%02x", valueByte & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required", exception);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }
}
