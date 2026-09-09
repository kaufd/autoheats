package com.wt.airconditioner;

/** Описанный GitHub Release APK, пригодный для обновления приложения. */
final class UpdateRelease {

    final String versionName;
    final int versionCode;
    final String apkUrl;

    UpdateRelease(String versionName, int versionCode, String apkUrl) {
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.apkUrl = apkUrl;
    }

    boolean isNewerThan(int installedVersionCode) {
        return versionCode > installedVersionCode;
    }
}
