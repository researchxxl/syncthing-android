package com.nutomic.syncthingandroid.superuser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.system.OsConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SuperuserBoundaryReviewTest {

    @Test
    public void privilegedEnvironmentPinsSecuritySensitivePaths() {
        Map<String, String> requested = new HashMap<>();
        requested.put("HOME", "/caller-controlled-home");
        requested.put("STHOMEDIR", "/caller-controlled-state");
        requested.put("SQLITE_TMPDIR", "/caller-controlled-tmp");
        requested.put("STTRACE", "folder");
        requested.put("GOGC", "75");

        Map<String, String> actual = SyncthingSuperuserService.sanitizeEnvironment(
                requested, "/fixed/home", "/fixed/state", "/fixed/tmp", "Syncthing");

        assertEquals("/fixed/home", actual.get("HOME"));
        assertEquals("/fixed/state", actual.get("STHOMEDIR"));
        assertEquals("/fixed/tmp", actual.get("SQLITE_TMPDIR"));
        assertEquals("1", actual.get("STMONITORED"));
        assertEquals("1", actual.get("STNOUPGRADE"));
        assertEquals("Syncthing", actual.get("STVERSIONEXTRA"));
        assertEquals("folder", actual.get("STTRACE"));
        assertEquals("75", actual.get("GOGC"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void privilegedEnvironmentRejectsLoaderInjection() {
        SyncthingSuperuserService.sanitizeEnvironment(
                Map.of("LD_PRELOAD", "/caller-controlled-loader"),
                "/fixed/home", "/fixed/state", "/fixed/tmp", "Syncthing");
    }

    @Test
    public void conflictResultIsBoundedAndReportsTruncation() {
        String[] conflicts = new String[2_000];
        for (int i = 0; i < conflicts.length; i++) {
            conflicts[i] = "nested/" + i + ".sync-conflict-20260907-120000-deviceid";
        }

        SuperuserStringListResult result = SyncthingSuperuserService
                .boundedConflictResult(conflicts);

        assertTrue(result.isSuccess());
        assertTrue(result.truncated);
        assertTrue(result.values.length < conflicts.length);
        assertEquals(conflicts[0], result.values[0]);
    }

    @Test
    public void conflictTruncationSurvivesBinderParceling() {
        SuperuserStringListResult original = SyncthingSuperuserService
                .boundedConflictResult(new String[2_000]);
        Parcel parcel = Parcel.obtain();
        try {
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            SuperuserStringListResult restored = SuperuserStringListResult.CREATOR
                    .createFromParcel(parcel);

            assertTrue(restored.truncated);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void secureFileOpenRejectsSymlinkWithoutTouchingTarget() throws Exception {
        Path directory = Files.createTempDirectory("secure-file");
        Path target = directory.resolve("target");
        Files.writeString(target, "sentinel", StandardCharsets.UTF_8);
        Path link = directory.resolve("link");
        Files.createSymbolicLink(link, target.getFileName());

        try {
            SecureFileAccess.OpenedFile descriptor = new JavaSecureFileAccess()
                    .openExistingRegular(link.toFile(), OsConstants.O_RDONLY);
            descriptor.close();
            throw new AssertionError("Expected symlink rejection");
        } catch (IOException expected) {
            // The final path component must never be followed.
        }

        assertEquals("sentinel", Files.readString(target, StandardCharsets.UTF_8));
    }

    @Test
    public void secureFileCreateRejectsPreexistingSymlink() throws Exception {
        Path directory = Files.createTempDirectory("secure-file-create");
        Path target = directory.resolve("target");
        Files.writeString(target, "sentinel", StandardCharsets.UTF_8);
        Path link = directory.resolve("link");
        Files.createSymbolicLink(link, target.getFileName());

        try {
            SecureFileAccess.OpenedFile descriptor = new JavaSecureFileAccess()
                    .createNewRegular(link.toFile());
            descriptor.close();
            throw new AssertionError("Expected symlink rejection");
        } catch (IOException expected) {
            // O_EXCL and O_NOFOLLOW reject the preexisting entry.
        }

        assertEquals("sentinel", Files.readString(target, StandardCharsets.UTF_8));
        assertTrue(Files.isSymbolicLink(link));
    }
}
