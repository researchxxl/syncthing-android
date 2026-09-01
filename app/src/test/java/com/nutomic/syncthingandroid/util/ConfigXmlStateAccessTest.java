package com.nutomic.syncthingandroid.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import com.nutomic.syncthingandroid.model.Gui;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccess;
import com.nutomic.syncthingandroid.service.state.SyncthingStateAccessException;
import com.nutomic.syncthingandroid.service.state.SyncthingStateFile;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigXmlStateAccessTest {

    private static final byte[] CONFIG = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<configuration version=\"28\">"
            + "<folder id=\"folder\" path=\"/tmp\" ignorePerms=\"true\">"
            + "<hashers>1</hashers></folder>"
            + "<gui tls=\"true\"><address>127.0.0.1:8384</address>"
            + "<apikey>test-key</apikey></gui>"
            + "<options><startBrowser>false</startBrowser></options>"
            + "</configuration>").getBytes(StandardCharsets.UTF_8);

    @Test
    public void loadConfigReadsThroughStateAccess() {
        InMemoryStateAccess access = new InMemoryStateAccess(CONFIG);
        ConfigXml config = new ConfigXml(application(), access);

        config.loadConfig();

        assertTrue(access.readCalls > 0);
    }

    @Test
    public void saveChangesWritesThroughStateAccess() {
        InMemoryStateAccess access = new InMemoryStateAccess(CONFIG);
        ConfigXml config = new ConfigXml(application(), access);
        config.loadConfig();

        Gui gui = config.getGui();
        gui.user = "user";
        gui.password = "password";
        gui.apiKey = "api-key";
        config.updateGui(gui);
        config.saveChanges();

        assertTrue(access.writeCalls > 0);
        assertTrue(access.lastWrite.length > 0);
    }

    @Test
    public void rootReadFailureBecomesConfigFailureWithoutFileFallback() {
        InMemoryStateAccess access = new InMemoryStateAccess(CONFIG);
        access.failRead = true;
        ConfigXml config = new ConfigXml(application(), access);

        try {
            config.loadConfig();
            fail("Expected config load failure");
        } catch (ConfigXml.OpenConfigException expected) {
            // A privileged state failure must not cause direct File access.
        }
        assertTrue(access.readCalls > 0);
    }

    private static Context application() {
        return RuntimeEnvironment.getApplication();
    }

    private static final class InMemoryStateAccess implements SyncthingStateAccess {
        private byte[] config;
        private boolean failRead;
        private int readCalls;
        private int writeCalls;
        private byte[] lastWrite = new byte[0];

        InMemoryStateAccess(byte[] config) {
            this.config = config.clone();
        }

        @Override
        public byte[] read(SyncthingStateFile file) throws SyncthingStateAccessException {
            readCalls++;
            if (failRead) {
                throw new SyncthingStateAccessException("privileged read failed");
            }
            return config.clone();
        }

        @Override
        public boolean exists(SyncthingStateFile file) {
            return true;
        }

        @Override
        public void writeAtomic(SyncthingStateFile file, byte[] data)
                throws SyncthingStateAccessException {
            writeCalls++;
            lastWrite = data.clone();
            config = data.clone();
        }

        @Override
        public void delete(SyncthingStateFile file) {
        }

        @Override
        public void verifyNormalAccess() {
        }
    }
}
