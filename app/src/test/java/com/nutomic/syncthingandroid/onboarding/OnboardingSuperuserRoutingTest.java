package com.nutomic.syncthingandroid.onboarding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

/** Plain-JVM regression coverage for configured-root onboarding routing. */
public class OnboardingSuperuserRoutingTest {

    @Test
    public void configuredRootDefersNormalConfigProbe() {
        assertTrue(OnboardingActivityKt.shouldTreatConfigAsAvailableForOnboarding(true, false));
    }

    @Test
    public void configuredRootRemainsConfiguredWhenNormalProbeWouldPass() {
        assertTrue(OnboardingActivityKt.shouldTreatConfigAsAvailableForOnboarding(true, true));
    }

    @Test
    public void normalParseableConfigContinuesToMain() {
        assertTrue(OnboardingActivityKt.shouldTreatConfigAsAvailableForOnboarding(false, true));
    }

    @Test
    public void normalMissingOrUnparseableConfigContinuesOnboarding() {
        assertFalse(OnboardingActivityKt.shouldTreatConfigAsAvailableForOnboarding(false, false));
    }

    @Test
    public void rootModeShortCircuitsBeforeTheNormalConfigProbeAtTheCallSite()
            throws IOException {
        String source = Files.readString(repoPath().resolve(
                "app/src/main/java/com/nutomic/syncthingandroid/onboarding/OnboardingActivity.kt"),
                StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private fun checkForParseableConfig()");
        int rootPreferenceRead = source.indexOf("AppPrefs.getUseRoot(this)", methodStart);
        int deferredReturn = source.indexOf(
                "return shouldTreatConfigAsAvailableForOnboarding(rootModeConfigured, false)",
                rootPreferenceRead);
        int normalConfigProbe = source.indexOf("Constants.getConfigFile(this)", deferredReturn);
        int configConstruction = source.indexOf("ConfigXml(this)", deferredReturn);
        int configLoad = source.indexOf("configParseTest.loadConfig()", deferredReturn);

        assertTrue(methodStart >= 0);
        assertTrue(rootPreferenceRead > methodStart);
        assertTrue(deferredReturn > rootPreferenceRead);
        assertTrue(normalConfigProbe > deferredReturn);
        assertTrue(configConstruction > normalConfigProbe);
        assertTrue(configLoad > normalConfigProbe);
        assertFalse(source.contains(
                "shouldTreatConfigAsAvailableForOnboarding(AppPrefs.getUseRoot(this), "));
    }

    @Test
    public void onboardingDoesNotOwnRootAuthorizationOrServiceBinding() throws IOException {
        String source = Files.readString(repoPath().resolve(
                "app/src/main/java/com/nutomic/syncthingandroid/onboarding/OnboardingActivity.kt"),
                StandardCharsets.UTF_8);

        for (String forbiddenEntryPoint : List.of(
                "RootService",
                "SuperuserClient",
                "bindService",
                "requestSuperuserMode",
                "ensureReadyForConfiguredMode")) {
            assertFalse("Unexpected root entry point: " + forbiddenEntryPoint,
                    source.contains(forbiddenEntryPoint));
        }
    }

    private static Path repoPath() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (path != null && !Files.isDirectory(path.resolve("app/src"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("Unable to locate repository root from test directory");
        }
        return path;
    }
}
