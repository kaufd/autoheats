package com.wt.airconditioner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Читает последний публичный релиз AutoHeat без токенов и сторонних SDK. */
final class GitHubReleaseClient {

    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/kaufd/autoheats/releases/latest";
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final Pattern VERSION_CODE = Pattern.compile("-vc(\\d+)\\.apk$");
    private static final int TIMEOUT_MS = 10_000;
    private static final int MAX_RESPONSE_CHARS = 2 * 1024 * 1024;

    /**
     * null означает успешный ответ без versioned native APK. Так выглядит
     * старый релиз AutoHeat-v3.apk: предлагать его как обновление нельзя,
     * потому что из имени невозможно безопасно узнать versionCode.
     */
    UpdateRelease latest() throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "AutoHeat-Android");

        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("GitHub Releases ответил HTTP " + status);
            }
            return parseRelease(readResponse(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    static UpdateRelease parseRelease(String json) throws IOException {
        try {
            JSONObject release = new JSONObject(json);
            String tag = release.optString("tag_name", "");
            String versionName = tag.startsWith("v") ? tag.substring(1) : tag;
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                return null;
            }

            UpdateRelease newestApk = null;
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                String name = asset.optString("name", "");
                String mime = asset.optString("content_type", "");
                Matcher versionCode = VERSION_CODE.matcher(name);
                if (!versionCode.find()
                        || !(APK_MIME.equals(mime) || name.endsWith(".apk"))) {
                    continue;
                }

                String apkUrl = asset.optString("browser_download_url", "");
                if (!apkUrl.startsWith("https://")) {
                    continue;
                }
                int code = Integer.parseInt(versionCode.group(1));
                if (newestApk == null || code > newestApk.versionCode) {
                    newestApk = new UpdateRelease(versionName, code, apkUrl);
                }
            }
            return newestApk;
        } catch (JSONException | NumberFormatException error) {
            throw new IOException("Некорректный ответ GitHub Releases", error);
        }
    }

    private static String readResponse(InputStream stream) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                response.append(buffer, 0, count);
                if (response.length() > MAX_RESPONSE_CHARS) {
                    throw new IOException("Ответ GitHub Releases слишком большой");
                }
            }
        }
        return response.toString();
    }
}
