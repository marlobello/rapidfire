package com.rapidfire.game.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rapidfire.game.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
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

            // Record successful check time
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()

            val release = JSONObject(json)
            val tagName = release.getString("tag_name") // e.g. "v1.2.0"
            val remoteVersion = tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (!isNewer(remoteVersion, currentVersion)) return@withContext null

            val releaseUrl = release.getString("html_url")
            val body = release.optString("body", "").take(500)

            UpdateInfo(remoteVersion, releaseUrl, body)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Open the GitHub release page in the browser so the user can download
     * the APK manually. This avoids REQUEST_INSTALL_PACKAGES which triggers
     * Google Play Protect warnings.
     */
    fun openReleasePage(updateInfo: UpdateInfo) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo.releaseUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Compare semantic versions. Returns true if [remote] is newer than [local].
     */
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
