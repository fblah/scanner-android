package com.fblah.scan

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub release metadata contains no document data. Android verifies APK signatures on install. */
class AppUpdater(private val context: Context) {
    data class Update(val code: Long, val url: String)

    fun check(): Update? {
        val connection = URL("https://api.github.com/repos/fblah/scanner-android/releases/tags/latest")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (connection.responseCode != 200) return null
            val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val code = Regex("build=(\\d+)").find(release.optString("body"))?.groupValues?.get(1)?.toLongOrNull() ?: return null
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            if (code <= installed) return null
            val assets = release.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val url = asset.getString("browser_download_url")
                if (asset.getString("name") == "scan.apk" && url.startsWith("https://github.com/fblah/scanner-android/releases/download/")) {
                    return Update(code, url)
                }
            }
            return null
        } finally { connection.disconnect() }
    }

    fun download(update: Update): Long {
        val request = DownloadManager.Request(Uri.parse(update.url))
            .setTitle("Scan update")
            .setDescription("Download complete? Open Scan to install.")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "scan-${update.code}.apk")
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        context.getSharedPreferences("updater", 0).edit().putLong("download", id).apply()
        return id
    }

    fun installDownloaded(): Boolean {
        val prefs = context.getSharedPreferences("updater", 0)
        val id = prefs.getLong("download", -1)
        if (id == -1L) return false
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return false
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_FAILED) { prefs.edit().remove("download").apply(); return false }
            if (status != DownloadManager.STATUS_SUCCESSFUL) return false
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return true
        }
        val uri = manager.getUriForDownloadedFile(id) ?: return false
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        prefs.edit().remove("download").apply()
        return true
    }
}
