package com.wt.airconditioner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GitHubReleaseClientTest {

    @Test
    public void parsesVersionCodeFromNativeApkName() throws Exception {
        UpdateRelease release = GitHubReleaseClient.parseRelease(releaseJson(
                "v0.7.0", "AutoHeat-native-v0.7.0-vc270.apk"));

        assertEquals("0.7.0", release.versionName);
        assertEquals(270, release.versionCode);
        assertEquals("https://example.test/autoheat.apk", release.apkUrl);
    }

    @Test
    public void comparesByAndroidVersionCodeNotSemanticTag() throws Exception {
        UpdateRelease release = GitHubReleaseClient.parseRelease(releaseJson(
                "v0.7.0", "AutoHeat-native-v0.7.0-vc270.apk"));

        assertTrue(release.isNewerThan(269));
        assertFalse(release.isNewerThan(270));
        assertFalse(release.isNewerThan(271));
    }

    @Test
    public void ignoresLegacyApkWithoutVersionCode() throws Exception {
        String json = "{\"tag_name\":\"v1.0.0\",\"assets\":[{"
                + "\"name\":\"AutoHeat-v3.apk\","
                + "\"content_type\":\"application/vnd.android.package-archive\","
                + "\"browser_download_url\":\"https://example.test/old.apk\"}]}";

        assertNull(GitHubReleaseClient.parseRelease(json));
    }

    @Test
    public void selectsHighestVersionCodeWhenReleaseHasSeveralApks() throws Exception {
        String json = "{\"tag_name\":\"v0.8.0\",\"assets\":["
                + assetJson("AutoHeat-native-v0.8.0-vc279.apk") + ","
                + assetJson("AutoHeat-native-v0.8.0-vc280.apk") + "]}";

        assertEquals(280, GitHubReleaseClient.parseRelease(json).versionCode);
    }

    private static String releaseJson(String tag, String name) {
        return "{\"tag_name\":\"" + tag + "\",\"assets\":["
                + assetJson(name) + "]}";
    }

    private static String assetJson(String name) {
        return "{\"name\":\"" + name + "\","
                + "\"content_type\":\"application/vnd.android.package-archive\","
                + "\"browser_download_url\":\"https://example.test/autoheat.apk\"}";
    }
}
