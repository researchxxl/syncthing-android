package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SyncthingImportPreferenceTest {

    @Test
    public void importedUseRootIsIgnoredAndDurablePreferenceStaysUnchanged() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences preferences = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context);
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, true).commit();

        SyncthingService service = new SyncthingService();
        service.mPreferences = preferences;
        File backup = new File(context.getCacheDir(), "import-preferences.dat");
        Map<String, Object> values = new HashMap<>();
        values.put(Constants.PREF_USE_ROOT, true);
        values.put(Constants.PREF_START_SERVICE_ON_BOOT, true);
        try (FileOutputStream output = new FileOutputStream(backup);
             ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(values);
        }

        assertTrue(service.importConfigSharedPrefs(backup));
        assertTrue(preferences.getBoolean(Constants.PREF_USE_ROOT, false));
        assertTrue(preferences.getBoolean(Constants.PREF_START_SERVICE_ON_BOOT, false));

        backup.delete();
        preferences.edit().putBoolean(Constants.PREF_USE_ROOT, false).commit();
    }
}
