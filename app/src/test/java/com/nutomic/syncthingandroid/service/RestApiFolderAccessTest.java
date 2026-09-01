package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertArrayEquals;

import android.content.Context;

import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.folder.SyncthingFolderAccess;
import com.nutomic.syncthingandroid.service.folder.SyncthingFolderAccessException;

import java.net.URL;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RestApiFolderAccessTest {

    @Test
    public void conflictDiscoveryUsesInjectedFolderCapability() throws Exception {
        FakeFolderAccess access = new FakeFolderAccess();
        Context context = RuntimeEnvironment.getApplication();
        RestApi api = new RestApi(context, new URL("http://127.0.0.1:8384"), "api-key",
                () -> { }, () -> { }, access);
        Folder folder = new Folder();
        folder.path = "/configured/folder";

        assertArrayEquals(new String[]{"relative-conflict"}, api.discoverConflictFiles(folder));
        assertArrayEquals(new String[]{"/configured/folder"}, access.requestedPaths);
    }

    private static final class FakeFolderAccess implements SyncthingFolderAccess {
        private String[] requestedPaths = new String[0];

        @Override
        public boolean canWrite(String absoluteFolderPath) {
            return true;
        }

        @Override
        public String[] findSyncConflicts(String absoluteConfiguredFolderPath) {
            requestedPaths = new String[]{absoluteConfiguredFolderPath};
            return new String[]{"relative-conflict"};
        }
    }
}
