package com.nutomic.syncthingandroid.service.execution;

import android.util.Log;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drains both core output streams so a process cannot block on a full pipe.
 * Non-captured output is appended to the wrapper-owned Syncthing log; captured output is bounded.
 */
public final class SyncthingProcessOutput {

    private static final String TAG = "SyncthingProcessOutput";
    private static final String TAG_NATIVE = "SyncthingNativeCode";
    private static final int MAX_CAPTURED_STDOUT_BYTES = 64 * 1024;

    private SyncthingProcessOutput() {
    }

    /** Waits for the process while consuming both streams. */
    public static SyncthingExecutionResult await(
            Process process, File logFile, boolean captureStdout)
            throws InterruptedException {
        AtomicReference<StringBuilder> captured = new AtomicReference<>(new StringBuilder());
        Thread stdout = consume(process.getInputStream(), logFile, captureStdout, captured, Log.INFO);
        Thread stderr = consume(process.getErrorStream(), logFile, false, captured, Log.WARN);
        int exitCode;
        try {
            exitCode = process.waitFor();
        } finally {
            joinQuietly(stdout);
            joinQuietly(stderr);
        }
        return new SyncthingExecutionResult(exitCode, captured.get().toString());
    }

    private static Thread consume(InputStream stream, File logFile, boolean captureStdout,
                                  AtomicReference<StringBuilder> captured, int priority) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, Charsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String lineWithNewline = line + "\n";
                    if (captureStdout && priority == Log.INFO) {
                        appendBounded(captured.get(), lineWithNewline);
                        Log.i(TAG_NATIVE, line);
                    } else {
                        appendToLog(logFile, lineWithNewline);
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to consume Syncthing output", e);
            }
        }, "syncthing-output");
        thread.start();
        return thread;
    }

    private static void appendToLog(File logFile, String line) {
        try {
            Files.append(line, logFile, Charsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "Failed to append Syncthing output to log", e);
        }
    }

    private static void appendBounded(StringBuilder output, String line) {
        int remaining = MAX_CAPTURED_STDOUT_BYTES
                - output.toString().getBytes(StandardCharsets.UTF_8).length;
        if (remaining <= 0) {
            return;
        }
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= remaining) {
            output.append(line);
            return;
        }
        output.append(new String(bytes, 0, remaining, StandardCharsets.UTF_8));
    }

    private static void joinQuietly(Thread thread) throws InterruptedException {
        if (thread != null) {
            thread.join();
        }
    }
}
