package com.nutomic.syncthingandroid.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import com.nutomic.syncthingandroid.R;
import com.nutomic.syncthingandroid.SyncthingApp;
import com.nutomic.syncthingandroid.activities.SyncthingActivity;
import com.nutomic.syncthingandroid.model.Device;
import com.nutomic.syncthingandroid.model.Folder;
import com.nutomic.syncthingandroid.service.RestApi;
import com.nutomic.syncthingandroid.service.SyncthingService;
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder;
import com.nutomic.syncthingandroid.superuser.SuperuserErrorCode;
import com.nutomic.syncthingandroid.superuser.SuperuserRuntimeStatus;
import com.nutomic.syncthingandroid.util.ConfigRouter;
import com.nutomic.syncthingandroid.views.DevicesAdapter;
import com.nutomic.syncthingandroid.views.FoldersAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.junit.runners.model.InitializationError;

/** Regression coverage for the list UI when configured root access becomes unavailable. */
@RunWith(ListFragmentSuperuserUnavailableTest.TestRunner.class)
@Config(application = SyncthingApp.class, manifest = Config.NONE)
public class ListFragmentSuperuserUnavailableTest {

    /** Supplies the generated debug resources that AGP does not publish to Robolectric here. */
    public static final class TestRunner extends RobolectricTestRunner {
        public TestRunner(Class<?> testClass) throws InitializationError {
            super(testClass);
        }

        @Override
        protected Properties getBuildSystemApiProperties() {
            Properties properties = new Properties();
            properties.setProperty("android_merged_manifest", buildPath(
                    "build", "intermediates", "packaged_manifests", "debug",
                    "processDebugManifestForPackage", "AndroidManifest.xml").toString());
            properties.setProperty("android_resource_apk", buildPath(
                    "build", "intermediates", "linked_resources_binary_format", "debug",
                    "processDebugResources", "linked-resources-binary-format-debug.ap_").toString());
            properties.setProperty("android_custom_package",
                    "com.github.catfriend1.syncthingfork.debug");
            return properties;
        }

        private static Path buildPath(String... components) {
            Path projectDirectory = Paths.get(System.getProperty("user.dir"));
            Path relativePath = Paths.get("");
            for (String component : components) {
                relativePath = relativePath.resolve(component);
            }
            Path path = projectDirectory.resolve(relativePath);
            if (Files.exists(path)) {
                return path;
            }
            return projectDirectory.resolve("app").resolve(relativePath);
        }
    }

    @Test
    public void folderListRendersUnavailableStateImmediately() {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());

        assertFalse(fragment.getListView().isShown());

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void deviceListRendersUnavailableStateImmediately() {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());

        assertFalse(fragment.getListView().isShown());

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void folderListRendersAuthorizationStateImmediately() {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);

        assertRequestingState(activity, fragment);
    }

    @Test
    public void deviceListRendersAuthorizationStateImmediately() {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);

        assertRequestingState(activity, fragment);
    }

    @Test
    public void folderListRendersRuntimeUnavailableStateWithoutLifecycleState() {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        activity.setRuntimeStatus(SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ROOT_UNAVAILABLE, false, "root unavailable"));

        fragment.onServiceStateChange(SyncthingService.State.DISABLED);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void deviceListRendersRuntimeUnavailableStateWithoutLifecycleState() {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        activity.setRuntimeStatus(SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ROOT_UNAVAILABLE, false, "root unavailable"));

        fragment.onServiceStateChange(SyncthingService.State.DISABLED);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void folderListKeepsNormalStateWhenServiceIsTemporarilyUnbound() {
        TestSyncthingActivity activity = createActivity();
        activity.serviceAvailable = false;
        FolderListFragment fragment = attach(activity, new FolderListFragment());

        fragment.onServiceStateChange(SyncthingService.State.DISABLED);

        assertEquals(activity.getString(R.string.folder_list_empty), emptyText(fragment));
    }

    @Test
    public void deviceListKeepsNormalStateWhenServiceIsTemporarilyUnbound() {
        TestSyncthingActivity activity = createActivity();
        activity.serviceAvailable = false;
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());

        fragment.onServiceStateChange(SyncthingService.State.DISABLED);

        assertEquals(activity.getString(R.string.no_devices_configured), emptyText(fragment));
    }

    @Test
    public void folderListAppliesUnavailableStateKnownBeforeViewCreation() {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = new FolderListFragment();
        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);

        attach(activity, fragment);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void deviceListAppliesUnavailableStateKnownBeforeViewCreation() {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = new DeviceListFragment();
        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);

        attach(activity, fragment);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void folderListClearsRowsAndSkipsConfigRouterWhileUnavailable() throws Exception {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        FoldersAdapter adapter = new FoldersAdapter(activity);
        adapter.add(folder("existing-folder"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        invokeUpdateList(fragment);
        invokeUpdateList(fragment);

        assertEquals(0, adapter.getCount());
        assertEquals(0, configRouter.folderCalls);
        assertUnavailableState(activity, fragment);
    }

    @Test
    public void deviceListClearsRowsAndSkipsConfigRouterWhileUnavailable() throws Exception {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        DevicesAdapter adapter = new DevicesAdapter(activity);
        adapter.add(device("existing-device"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        invokeUpdateList(fragment);
        invokeUpdateList(fragment);

        assertEquals(0, adapter.getCount());
        assertEquals(0, configRouter.deviceCalls);
        assertUnavailableState(activity, fragment);
    }

    @Test
    public void folderListClearsRowsAndSkipsReadsWhileAuthorizing() throws Exception {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        FoldersAdapter adapter = new FoldersAdapter(activity);
        adapter.add(folder("existing-folder"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);

        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());
        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        invokeUpdateList(fragment);

        assertEquals(0, adapter.getCount());
        assertEquals(0, configRouter.folderCalls);
        assertEquals(0, activity.apiCalls);
        assertRequestingState(activity, fragment);
    }

    @Test
    public void deviceListClearsRowsAndSkipsReadsWhileAuthorizing() throws Exception {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        DevicesAdapter adapter = new DevicesAdapter(activity);
        adapter.add(device("existing-device"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);

        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());
        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        invokeUpdateList(fragment);

        assertEquals(0, adapter.getCount());
        assertEquals(0, configRouter.deviceCalls);
        assertEquals(0, activity.apiCalls);
        assertRequestingState(activity, fragment);
    }

    @Test
    public void folderListReplaysPreBindingAuthorizationThroughBoundService() throws Exception {
        BoundSyncthingActivity activity = createBoundActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);
        TestSyncthingService service = new TestSyncthingService();
        service.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());

        activity.onServiceConnected(
                new ComponentName(activity, SyncthingService.class),
                new SyncthingServiceBinder(service));
        service.registerOnServiceStateChangeListener(fragment);

        assertRequestingState(activity, fragment);
        assertListContainerShown(fragment);
        assertEquals(0, configRouter.folderCalls);
        assertEquals(0, activity.apiCalls);
    }

    @Test
    public void deviceListReplaysPreBindingAuthorizationThroughBoundService() throws Exception {
        BoundSyncthingActivity activity = createBoundActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);
        TestSyncthingService service = new TestSyncthingService();
        service.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());

        activity.onServiceConnected(
                new ComponentName(activity, SyncthingService.class),
                new SyncthingServiceBinder(service));
        service.registerOnServiceStateChangeListener(fragment);

        assertRequestingState(activity, fragment);
        assertListContainerShown(fragment);
        assertEquals(0, configRouter.deviceCalls);
        assertEquals(0, activity.apiCalls);
    }

    @Test
    public void folderListReplaysPreBindingUnavailableThroughBoundService() throws Exception {
        BoundSyncthingActivity activity = createBoundActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        FoldersAdapter adapter = new FoldersAdapter(activity);
        adapter.add(folder("stale-folder"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);
        TestSyncthingService service = new TestSyncthingService();
        service.setRuntimeStatus(SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ROOT_UNAVAILABLE,
                false,
                "root unavailable"));

        activity.onServiceConnected(
                new ComponentName(activity, SyncthingService.class),
                new SyncthingServiceBinder(service));
        service.registerOnServiceStateChangeListener(fragment);

        assertUnavailableState(activity, fragment);
        assertListContainerShown(fragment);
        assertEquals(0, adapter.getCount());
        assertEquals(0, configRouter.folderCalls);
        assertEquals(0, activity.apiCalls);
    }

    @Test
    public void deviceListReplaysPreBindingUnavailableThroughBoundService() throws Exception {
        BoundSyncthingActivity activity = createBoundActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        DevicesAdapter adapter = new DevicesAdapter(activity);
        adapter.add(device("stale-device"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        setField(fragment, "mConfigRouter", configRouter);
        TestSyncthingService service = new TestSyncthingService();
        service.setRuntimeStatus(SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ROOT_UNAVAILABLE,
                false,
                "root unavailable"));

        activity.onServiceConnected(
                new ComponentName(activity, SyncthingService.class),
                new SyncthingServiceBinder(service));
        service.registerOnServiceStateChangeListener(fragment);

        assertUnavailableState(activity, fragment);
        assertListContainerShown(fragment);
        assertEquals(0, adapter.getCount());
        assertEquals(0, configRouter.deviceCalls);
        assertEquals(0, activity.apiCalls);
    }

    @Test
    public void folderListRecoversAfterAuthorizationThroughBoundServiceNotification() throws Exception {
        BoundSyncthingActivity activity = createBoundActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        configRouter.folders.add(folder("recovered-folder"));
        setField(fragment, "mConfigRouter", configRouter);
        TestSyncthingService service = new TestSyncthingService();
        service.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());

        activity.onServiceConnected(
                new ComponentName(activity, SyncthingService.class),
                new SyncthingServiceBinder(service));
        service.registerOnServiceStateChangeListener(fragment);
        assertRequestingState(activity, fragment);

        service.setRuntimeStatus(SuperuserRuntimeStatus.ready());
        service.notifyCurrentStateListeners();

        assertEquals(activity.getString(R.string.folder_list_empty), emptyText(fragment));
        assertFalse(fragment.getListView().getEmptyView().isShown());
        assertEquals(1, configRouter.folderCalls);
        assertEquals(1, ((FoldersAdapter) fragment.getListAdapter()).getCount());
        assertTrue(fragment.getListView().isShown());
    }

    @Test
    public void deviceListRecoversAfterAuthorizationThroughBoundServiceNotification() throws Exception {
        BoundSyncthingActivity activity = createBoundActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        configRouter.devices.add(device("recovered-device"));
        setField(fragment, "mConfigRouter", configRouter);
        TestSyncthingService service = new TestSyncthingService();
        service.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());

        activity.onServiceConnected(
                new ComponentName(activity, SyncthingService.class),
                new SyncthingServiceBinder(service));
        service.registerOnServiceStateChangeListener(fragment);
        assertRequestingState(activity, fragment);

        service.setRuntimeStatus(SuperuserRuntimeStatus.ready());
        service.notifyCurrentStateListeners();

        assertEquals(activity.getString(R.string.no_devices_configured), emptyText(fragment));
        assertFalse(fragment.getListView().getEmptyView().isShown());
        assertEquals(1, configRouter.deviceCalls);
        assertEquals(1, ((DevicesAdapter) fragment.getListAdapter()).getCount());
        assertTrue(fragment.getListView().isShown());
    }

    @Test
    public void folderListRestoresNormalStateAfterAuthorizationCompletes() throws Exception {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        configRouter.folders.add(folder("recovered-folder"));
        setField(fragment, "mConfigRouter", configRouter);
        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());
        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        assertRequestingState(activity, fragment);

        activity.setRuntimeStatus(SuperuserRuntimeStatus.ready());
        fragment.onServiceStateChange(SyncthingService.State.ACTIVE);

        assertEquals(activity.getString(R.string.folder_list_empty), emptyText(fragment));
        assertFalse(fragment.getListView().getEmptyView().isShown());

        assertEquals(1, configRouter.folderCalls);
        assertEquals(1, ((FoldersAdapter) fragment.getListAdapter()).getCount());
        assertTrue(fragment.getListView().isShown());
    }

    @Test
    public void deviceListRestoresNormalStateAfterAuthorizationCompletes() throws Exception {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        configRouter.devices.add(device("recovered-device"));
        setField(fragment, "mConfigRouter", configRouter);
        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());
        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        assertRequestingState(activity, fragment);

        activity.setRuntimeStatus(SuperuserRuntimeStatus.ready());
        fragment.onServiceStateChange(SyncthingService.State.ACTIVE);

        assertEquals(activity.getString(R.string.no_devices_configured), emptyText(fragment));
        assertFalse(fragment.getListView().getEmptyView().isShown());

        assertEquals(1, configRouter.deviceCalls);
        assertEquals(1, ((DevicesAdapter) fragment.getListAdapter()).getCount());
        assertTrue(fragment.getListView().isShown());
    }

    @Test
    public void folderListTransitionsFromAuthorizationToUnavailable() {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());
        fragment.onServiceStateChange(SyncthingService.State.DISABLED);
        assertRequestingState(activity, fragment);

        activity.setRuntimeStatus(SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ROOT_UNAVAILABLE, false, "root unavailable"));
        fragment.onServiceStateChange(SyncthingService.State.DISABLED);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void deviceListTransitionsFromAuthorizationToUnavailable() {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        activity.setRuntimeStatus(SuperuserRuntimeStatus.authorizing());
        fragment.onServiceStateChange(SyncthingService.State.DISABLED);
        assertRequestingState(activity, fragment);

        activity.setRuntimeStatus(SuperuserRuntimeStatus.unavailable(
                SuperuserErrorCode.ROOT_UNAVAILABLE, false, "root unavailable"));
        fragment.onServiceStateChange(SyncthingService.State.DISABLED);

        assertUnavailableState(activity, fragment);
    }

    @Test
    public void folderListResumesNormalRefreshAfterUnavailable() throws Exception {
        TestSyncthingActivity activity = createActivity();
        FolderListFragment fragment = attach(activity, new FolderListFragment());
        FoldersAdapter adapter = new FoldersAdapter(activity);
        adapter.add(folder("stale-folder"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        configRouter.folders.add(folder("recovered-folder"));
        setField(fragment, "mConfigRouter", configRouter);

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        assertUnavailableState(activity, fragment);
        assertEquals(0, adapter.getCount());

        fragment.onServiceStateChange(SyncthingService.State.ACTIVE);
        assertEquals(activity.getString(R.string.folder_list_empty), emptyText(fragment));

        invokeUpdateList(fragment);

        assertEquals(1, configRouter.folderCalls);
        assertEquals(1, ((FoldersAdapter) fragment.getListAdapter()).getCount());
        assertTrue(fragment.getListView().isShown());
    }

    @Test
    public void deviceListResumesNormalRefreshAfterUnavailable() throws Exception {
        TestSyncthingActivity activity = createActivity();
        DeviceListFragment fragment = attach(activity, new DeviceListFragment());
        DevicesAdapter adapter = new DevicesAdapter(activity);
        adapter.add(device("stale-device"));
        setField(fragment, "mAdapter", adapter);
        fragment.setListAdapter(adapter);
        RecordingConfigRouter configRouter = new RecordingConfigRouter(activity);
        configRouter.devices.add(device("recovered-device"));
        setField(fragment, "mConfigRouter", configRouter);

        fragment.onServiceStateChange(SyncthingService.State.SUPERUSER_UNAVAILABLE);
        assertUnavailableState(activity, fragment);
        assertEquals(0, adapter.getCount());

        fragment.onServiceStateChange(SyncthingService.State.ACTIVE);
        assertEquals(activity.getString(R.string.no_devices_configured), emptyText(fragment));

        invokeUpdateList(fragment);

        assertEquals(1, configRouter.deviceCalls);
        assertEquals(1, ((DevicesAdapter) fragment.getListAdapter()).getCount());
        assertTrue(fragment.getListView().isShown());
    }

    private static TestSyncthingActivity createActivity() {
        // The local test resource table omits AppCompat's private vector probe drawable.
        // Marking that probe complete keeps this focused host independent of the real activity.
        try {
            Class<?> resourceManagerClass = Class.forName(
                    "androidx.appcompat.widget.ResourceManagerInternal");
            Method getResourceManager = resourceManagerClass.getMethod("get");
            Object resourceManager = getResourceManager.invoke(null);
            Field vectorCheck = resourceManagerClass.getDeclaredField(
                    "mHasCheckedVectorDrawableSetup");
            vectorCheck.setAccessible(true);
            vectorCheck.setBoolean(resourceManager, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to prepare the AppCompat test host", e);
        }
        ActivityController<TestSyncthingActivity> controller =
                Robolectric.buildActivity(TestSyncthingActivity.class);
        controller.get().setTheme(R.style.Theme_Syncthing);
        return controller.setup().get();
    }

    private static BoundSyncthingActivity createBoundActivity() {
        prepareAppCompatVectorDrawableCheck();
        ActivityController<BoundSyncthingActivity> controller =
                Robolectric.buildActivity(BoundSyncthingActivity.class);
        controller.get().setTheme(R.style.Theme_Syncthing);
        return controller.setup().get();
    }

    private static void prepareAppCompatVectorDrawableCheck() {
        // The local test resource table omits AppCompat's private vector probe drawable.
        // Marking that probe complete keeps this focused host independent of the real activity.
        try {
            Class<?> resourceManagerClass = Class.forName(
                    "androidx.appcompat.widget.ResourceManagerInternal");
            Method getResourceManager = resourceManagerClass.getMethod("get");
            Object resourceManager = getResourceManager.invoke(null);
            Field vectorCheck = resourceManagerClass.getDeclaredField(
                    "mHasCheckedVectorDrawableSetup");
            vectorCheck.setAccessible(true);
            vectorCheck.setBoolean(resourceManager, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to prepare the AppCompat test host", e);
        }
    }

    private static <T extends Fragment> T attach(SyncthingActivity activity, T fragment) {
        // Keep the pager-driven refresh timer out of these direct state-rendering tests.
        fragment.setUserVisibleHint(false);
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        return fragment;
    }

    private static void assertUnavailableState(SyncthingActivity activity,
                                                ListFragment fragment) {
        View emptyView = fragment.getListView().getEmptyView();
        assertNotNull(emptyView);
        assertTrue(emptyView.isShown());
        assertEquals(activity.getString(R.string.syncthing_superuser_unavailable),
                emptyText(fragment));
    }

    private static void assertRequestingState(SyncthingActivity activity,
                                               ListFragment fragment) {
        View emptyView = fragment.getListView().getEmptyView();
        assertNotNull(emptyView);
        assertTrue(emptyView.isShown());
        assertEquals(activity.getString(R.string.use_root_requesting), emptyText(fragment));
    }

    private static String emptyText(ListFragment fragment) {
        View emptyView = fragment.getListView().getEmptyView();
        assertNotNull(emptyView);
        assertTrue(emptyView instanceof TextView);
        return ((TextView) emptyView).getText().toString();
    }

    private static void invokeUpdateList(Object fragment) throws Exception {
        Method updateList = fragment.getClass().getDeclaredMethod("updateList");
        updateList.setAccessible(true);
        updateList.invoke(fragment);
    }

    private static void assertListContainerShown(ListFragment fragment) throws Exception {
        View listContainer = (View) getField(ListFragment.class, fragment, "mListContainer");
        View progressContainer = (View) getField(ListFragment.class, fragment, "mProgressContainer");
        assertNotNull(listContainer);
        assertNotNull(progressContainer);
        assertTrue(listContainer.isShown());
        assertFalse(progressContainer.isShown());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Folder folder(String id) {
        Folder folder = new Folder();
        folder.id = id;
        return folder;
    }

    private static Device device(String id) {
        Device device = new Device();
        device.deviceID = id;
        return device;
    }

    private static final class TestSyncthingActivity extends SyncthingActivity {
        private final TestSyncthingService service = new TestSyncthingService();
        private boolean serviceAvailable = true;
        private int apiCalls;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new FrameLayout(this));
        }

        @Override
        public boolean bindService(Intent service, ServiceConnection connection, int flags) {
            return false;
        }

        @Override
        public void unbindService(ServiceConnection connection) {
            // Keep the focused fragment tests from unbinding a service they never bound.
        }

        @Override
        public SyncthingService getService() {
            return serviceAvailable ? service : null;
        }

        @Override
        public RestApi getApi() {
            apiCalls++;
            return null;
        }

        private void setRuntimeStatus(SuperuserRuntimeStatus status) {
            service.setRuntimeStatus(status);
        }
    }

    private static final class BoundSyncthingActivity extends SyncthingActivity {
        private int apiCalls;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new FrameLayout(this));
        }

        @Override
        public boolean bindService(Intent service, ServiceConnection connection, int flags) {
            return false;
        }

        @Override
        public void unbindService(ServiceConnection connection) {
            // Keep the focused fragment tests from unbinding a service they never bound.
        }

        @Override
        public RestApi getApi() {
            apiCalls++;
            return null;
        }
    }

    private static final class TestSyncthingService extends SyncthingService {
        private SuperuserRuntimeStatus runtimeStatus = SuperuserRuntimeStatus.normal();

        @Override
        public SuperuserRuntimeStatus getSuperuserRuntimeStatus() {
            return runtimeStatus;
        }

        private void setRuntimeStatus(SuperuserRuntimeStatus status) {
            runtimeStatus = status;
        }

        private void notifyCurrentStateListeners() {
            try {
                Method notifyListeners = SyncthingService.class.getDeclaredMethod(
                        "notifyServiceStateListeners");
                notifyListeners.setAccessible(true);
                notifyListeners.invoke(this);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to notify service state listeners", e);
            }
        }
    }

    private static final class RecordingConfigRouter extends ConfigRouter {
        private final List<Folder> folders = new ArrayList<>();
        private final List<Device> devices = new ArrayList<>();
        private int folderCalls;
        private int deviceCalls;

        private RecordingConfigRouter(SyncthingActivity activity) {
            super(activity);
        }

        @Override
        public List<Folder> getFolders(RestApi restApi) {
            folderCalls++;
            return new ArrayList<>(folders);
        }

        @Override
        public List<Device> getDevices(RestApi restApi, Boolean includeLocal) {
            deviceCalls++;
            return new ArrayList<>(devices);
        }
    }
}
