package com.rapidfire.game.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.rapidfire.game.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

sealed class UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult()
    data object UpToDate : UpdateResult()
    data object Throttled : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

class AppUpdater(private val context: Context) {

    private var activeDownloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null

    companion object {
        private const val GITHUB_OWNER = "marlobello"
        private const val GITHUB_REPO = "rapidfire"
        private const val API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val UPDATES_DIR = "updates"
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        if (!force) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) {
                return@withContext UpdateResult.Throttled
            }
        }

        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                connection.disconnect()
                return@withContext UpdateResult.Error("Server returned $responseCode")
            }

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            val release = JSONObject(json)
            val tagName = release.getString("tag_name")
            val remoteVersion = tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (!isNewer(remoteVersion, currentVersion)) {
                return@withContext UpdateResult.UpToDate
            }

            val assets = release.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) return@withContext UpdateResult.Error("No APK in release")

            val body = release.optString("body", "").take(500)
            UpdateResult.Available(UpdateInfo(remoteVersion, apkUrl, body))
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Download APK to private app storage and auto-launch the installer
     * when complete. Uses FileProvider for secure content URI — this
     * pattern is recognized by Play Protect as legitimate.
     */
    fun downloadAndInstall(
        updateInfo: UpdateInfo,
        onDownloadStarted: () -> Unit = {},
        onInstallLaunched: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val updatesDir = File(context.getExternalFilesDir(null), UPDATES_DIR)
        updatesDir.mkdirs()
        // Clean up old APKs
        updatesDir.listFiles()?.filter { it.extension == "apk" }?.forEach { it.delete() }

        val apkFile = File(updatesDir, "rapidfire-v${updateInfo.versionName}.apk")

        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("Rapid Fire v${updateInfo.versionName}")
            .setDescription("Downloading update…")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationUri(Uri.fromFile(apkFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Unregister any previous receiver
        unregisterReceiver()

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != activeDownloadId) return
                unregisterReceiver()

                // Verify download succeeded
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    )
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        launchInstaller(apkFile)
                        onInstallLaunched()
                    } else {
                        onError("Download failed")
                    }
                }
                cursor.close()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
        }

        activeDownloadId = dm.enqueue(request)
        onDownloadStarted()
    }

    private fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun unregisterReceiver() {
        downloadReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            downloadReceiver = null
        }
    }

    internal fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
