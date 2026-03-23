package com.rapidfire.game.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.rapidfire.game.BuildConfig
import com.rapidfire.game.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String
)

class AppUpdater(private val context: Context) {

    companion object {
        private const val GITHUB_OWNER = "marlobello"
        private const val GITHUB_REPO = "rapidfire"
        private const val API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    /**
     * Check for an available update. Returns [UpdateInfo] if a newer version
     * exists, or null if already up-to-date (or on network error).
     */
    suspend fun checkForUpdate(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!force) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0)
            if (System.currentTimeMillis() - lastCheck < CHECK_INTERVAL_MS) return@withContext null
        }

        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode != 200) return@withContext null

            val json = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            val release = JSONObject(json)
            val tagName = release.getString("tag_name")
            val remoteVersion = tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (!isNewer(remoteVersion, currentVersion)) return@withContext null

            // Find the APK asset
            val assets = release.getJSONArray("assets")
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }
            if (apkUrl == null) return@withContext null

            val body = release.optString("body", "").take(500)
            UpdateInfo(remoteVersion, apkUrl, body)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Download the APK via the system DownloadManager to the public Downloads
     * folder. The completion notification lets the user tap to install.
     * No REQUEST_INSTALL_PACKAGES needed — the system handles the install.
     */
    fun downloadUpdate(updateInfo: UpdateInfo) {
        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl))
            .setTitle("Rapid Fire v${updateInfo.versionName}")
            .setDescription("Tap when complete to install")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "rapidfire-v${updateInfo.versionName}.apk"
            )
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)

        Toast.makeText(
            context,
            context.getString(R.string.download_started),
            Toast.LENGTH_LONG
        ).show()
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
