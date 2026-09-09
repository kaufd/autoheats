package com.wt.airconditioner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** UI и Android 9 flow проверки, загрузки и установки обновления. */
final class AppUpdateController {

    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final long NO_DOWNLOAD = -1L;

    private enum State {
        INITIAL,
        CHECKING,
        LATEST,
        AVAILABLE,
        WAITING_FOR_PERMISSION,
        DOWNLOADING,
        OPENING_INSTALLER,
        ERROR,
    }

    private final Activity activity;
    private final GitHubReleaseClient releaseClient = new GitHubReleaseClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final DownloadManager downloads;
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, NO_DOWNLOAD);
            if (completed == downloadId) {
                onDownloadComplete(completed);
            }
        }
    };

    private TextView statusView;
    private TextView actionView;
    private State state = State.INITIAL;
    private UpdateRelease availableRelease;
    private String errorMessage;
    private boolean automaticCheckStarted;
    private boolean destroyed;
    private long downloadId = NO_DOWNLOAD;
    private File downloadedApk;

    /** На единственной целевой платформе, Android 9, флагов receiver ещё нет. */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    AppUpdateController(Activity activity) {
        this.activity = activity;
        downloads = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        activity.registerReceiver(downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    void bind(View page) {
        TextView version = page.findViewById(R.id.appVersion);
        version.setText(String.format(Locale.US, "%s (%d)",
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        statusView = page.findViewById(R.id.updateStatus);
        actionView = page.findViewById(R.id.updateAction);
        actionView.setOnClickListener(view -> {
            if (availableRelease == null) {
                checkForUpdate();
            } else {
                requestInstall();
            }
        });
        render();
    }

    /** Автоматически только при первом открытии настроек за этот запуск. */
    void checkAutomatically() {
        if (automaticCheckStarted) {
            return;
        }
        automaticCheckStarted = true;
        checkForUpdate();
    }

    /**
     * Экран вернулся из системного: с выдачи разрешения на установку или от
     * установщика APK. Куда именно уходили, говорит state — отдельных флагов
     * для этого больше нет. Они дублировали два его значения и могли с ними
     * разъехаться: сброшенный флаг при непереключённом state оставлял flow
     * обновления в состоянии, из которого его уже никто не двигал.
     */
    void onResume() {
        if (state == State.WAITING_FOR_PERMISSION) {
            if (activity.getPackageManager().canRequestPackageInstalls()) {
                beginDownload();
            } else {
                showError("Разрешите AutoHeat устанавливать приложения");
            }
        } else if (state == State.OPENING_INSTALLER) {
            /**
             * Установщик закрыли — принял человек обновление или нет, отсюда не
             * видно: предлагаем ту же кнопку «Обновить».
             */
            state = State.AVAILABLE;
            render();
        }
    }

    void destroy() {
        destroyed = true;
        executor.shutdownNow();
        activity.unregisterReceiver(downloadReceiver);
    }

    private void checkForUpdate() {
        if (state == State.CHECKING || state == State.DOWNLOADING) {
            return;
        }
        availableRelease = null;
        errorMessage = null;
        state = State.CHECKING;
        render();

        executor.execute(() -> {
            try {
                UpdateRelease release = releaseClient.latest();
                activity.runOnUiThread(() -> {
                    if (destroyed) {
                        return;
                    }
                    if (release != null && release.isNewerThan(BuildConfig.VERSION_CODE)) {
                        availableRelease = release;
                        state = State.AVAILABLE;
                    } else {
                        state = State.LATEST;
                    }
                    render();
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (!destroyed) {
                        showError("Не удалось проверить обновление");
                    }
                });
            }
        });
    }

    private void requestInstall() {
        if (availableRelease == null) {
            return;
        }
        if (activity.getPackageManager().canRequestPackageInstalls()) {
            beginDownload();
            return;
        }

        state = State.WAITING_FOR_PERMISSION;
        render();
        Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        try {
            activity.startActivity(permission);
        } catch (ActivityNotFoundException error) {
            /**
             * Экран не открылся — ждать возвращения неоткуда, showError уводит
             * state из WAITING_FOR_PERMISSION, и ближайший onResume это увидит.
             */
            showError("Откройте разрешение на установку APK в настройках Android");
        }
    }

    private void beginDownload() {
        if (availableRelease == null || state == State.DOWNLOADING) {
            return;
        }
        String fileName = String.format(Locale.US, "AutoHeat-update-v%s-vc%d-%d.apk",
                availableRelease.versionName, availableRelease.versionCode,
                System.currentTimeMillis());
        File downloadsDirectory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDirectory == null) {
            showError("Хранилище для обновления недоступно");
            return;
        }
        downloadedApk = new File(downloadsDirectory, fileName);
        DownloadManager.Request request = new DownloadManager.Request(
                Uri.parse(availableRelease.apkUrl))
                .setTitle("Обновление AutoHeat " + availableRelease.versionName)
                .setDescription("Загрузка установочного APK")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS,
                        fileName);
        try {
            downloadId = downloads.enqueue(request);
            state = State.DOWNLOADING;
            render();
        } catch (RuntimeException error) {
            showError("Не удалось начать загрузку обновления");
        }
    }

    private void onDownloadComplete(long completedId) {
        try (Cursor cursor = downloads.query(
                new DownloadManager.Query().setFilterById(completedId))) {
            if (cursor == null || !cursor.moveToFirst()) {
                showError("Загруженный APK не найден");
                return;
            }
            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusColumn < 0
                    || cursor.getInt(statusColumn) != DownloadManager.STATUS_SUCCESSFUL) {
                showError("Не удалось загрузить обновление");
                return;
            }
        }

        Uri apk = downloads.getUriForDownloadedFile(completedId);
        if (apk == null) {
            showError("Загруженный APK не найден");
            return;
        }
        PackageInfo archive = downloadedApk == null ? null
                : activity.getPackageManager().getPackageArchiveInfo(
                        downloadedApk.getAbsolutePath(), 0);
        if (archive == null
                || !activity.getPackageName().equals(archive.packageName)
                || archive.getLongVersionCode() != availableRelease.versionCode) {
            showError("Загружен неподходящий APK AutoHeat");
            return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apk, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            state = State.OPENING_INSTALLER;
            render();
            activity.startActivity(install);
        } catch (ActivityNotFoundException error) {
            showError("Системный установщик APK не найден");
        }
    }

    private void showError(String message) {
        errorMessage = message;
        state = State.ERROR;
        render();
    }

    private void render() {
        if (statusView == null || actionView == null) {
            return;
        }
        actionView.setAlpha(1f);
        switch (state) {
            case CHECKING:
                showAction("Проверка…", false);
                hideStatus();
                break;
            case LATEST:
                actionView.setVisibility(View.GONE);
                showStatus("Установлена последняя версия");
                break;
            case AVAILABLE:
                showAction("Обновить", true);
                showStatus("Доступна версия " + availableRelease.versionName);
                break;
            case WAITING_FOR_PERMISSION:
                showAction("Разрешите установку", false);
                showStatus("Ожидание разрешения Android");
                break;
            case DOWNLOADING:
                showAction("Загрузка…", false);
                showStatus("Загружается версия " + availableRelease.versionName);
                break;
            case OPENING_INSTALLER:
                showAction("Установка…", false);
                showStatus("Открывается установщик Android");
                break;
            case ERROR:
                showAction(availableRelease == null ? "Проверить обновление" : "Обновить", true);
                showStatus(errorMessage);
                break;
            case INITIAL:
            default:
                showAction("Проверить обновление", true);
                hideStatus();
                break;
        }
    }

    private void showAction(String text, boolean enabled) {
        actionView.setText(text);
        actionView.setEnabled(enabled);
        actionView.setAlpha(enabled ? 1f : 0.6f);
        actionView.setVisibility(View.VISIBLE);
    }

    private void showStatus(String text) {
        statusView.setText(text);
        statusView.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        statusView.setVisibility(View.GONE);
    }
}
