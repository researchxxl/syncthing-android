package com.nutomic.syncthingandroid.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.activities.MainActivity;
import com.nutomic.syncthingandroid.settings.SettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Verifies root-specific notification copy, persistence policy, and destination. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NotificationHandlerSuperuserTest {

    @Test
    public void superuserUnavailableUsesRootSpecificCopyAndBehaviorDestination() {
        assertEquals(R.string.syncthing_superuser_unavailable,
                NotificationHandler.persistentNotificationTextRes(
                        SyncthingService.State.SUPERUSER_UNAVAILABLE));
        assertTrue(NotificationHandler.requiresPersistentUnavailableNotification(
                SyncthingService.State.SUPERUSER_UNAVAILABLE));

        Intent intent = NotificationHandler.persistentContentIntent(
                RuntimeEnvironment.getApplication(), SyncthingService.State.SUPERUSER_UNAVAILABLE);
        assertEquals(SettingsActivity.class.getName(), intent.getComponent().getClassName());
        assertEquals("Behavior", intent.getStringExtra(SettingsActivity.EXTRA_START_DESTINATION));
    }

    @Test
    public void normalStatesKeepExistingCopyAndMainDestination() {
        assertEquals(R.string.syncthing_disabled,
                NotificationHandler.persistentNotificationTextRes(SyncthingService.State.DISABLED));
        assertEquals(R.string.syncthing_terminated,
                NotificationHandler.persistentNotificationTextRes(SyncthingService.State.ERROR));
        assertFalse(NotificationHandler.requiresPersistentUnavailableNotification(
                SyncthingService.State.DISABLED));

        Intent intent = NotificationHandler.persistentContentIntent(
                RuntimeEnvironment.getApplication(), SyncthingService.State.DISABLED);
        assertEquals(MainActivity.class.getName(), intent.getComponent().getClassName());
    }
}
