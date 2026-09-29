package com.nutomic.syncthingandroid.runtime;

import android.content.Context;

import com.nutomic.syncthingandroid.service.Constants;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Normal-mode configuration storage preserving the existing temporary-file replacement flow. */
final class AppUidConfigStorage implements ConfigStorage {
    private final File configFile;
    private final File temporaryConfigFile;

    AppUidConfigStorage(Context context) {
        configFile = Constants.getConfigFile(context);
        temporaryConfigFile = Constants.getConfigTempFile(context);
    }

    @Override
    public boolean canRead() {
        return configFile.canRead();
    }

    @Override
    public byte[] load() throws IOException {
        try (FileInputStream input = new FileInputStream(configFile)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        }
    }

    @Override
    public boolean canWrite() {
        return configFile.canWrite();
    }

    @Override
    public void save(byte[] contents) throws IOException {
        try (FileOutputStream output = new FileOutputStream(temporaryConfigFile)) {
            output.write(contents);
        }
        // Preserve the existing normal-mode behavior: renameTo reports failure through its
        // boolean result and does not throw an additional storage exception.
        temporaryConfigFile.renameTo(configFile);
    }
}
