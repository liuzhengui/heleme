package com.zhengui.waterreminder.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.zhengui.waterreminder.BuildConfig
import com.zhengui.waterreminder.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val NOTIFICATION_CHANNEL_ID = "update_download"
    private const val NOTIFICATION_ID = 2001

    // Gitee API 优先（国内访问更快），GitHub 作为 fallback
    private const val GITEE_API = "https://gitee.com/api/v5/repos/gui_1124/heleme/releases/latest"
    private const val GITHUB_API = "https://api.github.com/repos/52uni/heleme/releases/latest"

    data class UpdateInfo(
        val tagName: String,
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val body: String
    )

    /**
     * 检查更新：先 Gitee 后 GitHub
     * @return 有新版本时返回 UpdateInfo，否则返回 null
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val info = fetchFromGitee() ?: fetchFromGitHub() ?: return@withContext null

        if (isNewerVersion(info.tagName, BuildConfig.VERSION_NAME)) {
            Log.d(TAG, "发现新版本: ${info.tagName}, 当前: ${BuildConfig.VERSION_NAME}")
            info
        } else {
            Log.d(TAG, "已是最新版本: ${BuildConfig.VERSION_NAME}")
            null
        }
    }

    /**
     * 语义化版本比较：a > b 返回 true
     * "1.2.1" > "1.2" → true, "1.2" > "1.2" → false
     */
    private fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val remote = remoteTag.split(".").map { it.toIntOrNull() ?: 0 }
        val local = localVersion.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remote.size, local.size)) {
            val r = remote.getOrElse(i) { 0 }
            val l = local.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private suspend fun fetchFromGitee(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "尝试 Gitee API...")
            val json = httpGet(GITEE_API)
            parseGitee(json)
        } catch (e: Exception) {
            Log.w(TAG, "Gitee API 失败: ${e.message}")
            null
        }
    }

    private suspend fun fetchFromGitHub(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "尝试 GitHub API...")
            val json = httpGet(GITHUB_API) { connection ->
                connection.setRequestProperty("Accept", "application/vnd.github+json")
            }
            parseGitHub(json)
        } catch (e: Exception) {
            Log.w(TAG, "GitHub API 失败: ${e.message}")
            null
        }
    }

    private fun httpGet(urlStr: String, configure: (HttpURLConnection) -> Unit = {}): String {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            configure(connection)
            connection.connect()
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                throw Exception("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseGitee(json: String): UpdateInfo {
        val obj = JSONObject(json)
        val tagName = obj.optString("tag_name", "").trimStart('v')
        val body = obj.optString("body", "")

        val assets = obj.optJSONArray("assets")
        val downloadUrl = assets?.takeIf { it.length() > 0 }?.let {
            it.getJSONObject(0).optString("browser_download_url", "")
        } ?: throw Exception("Gitee: no asset found")

        val versionCode = tagName.toIntOrNull() ?: parseVersionCode(tagName)

        return UpdateInfo(
            tagName = tagName,
            versionCode = versionCode,
            versionName = tagName,
            downloadUrl = downloadUrl,
            body = stripMarkdown(body)
        )
    }

    private fun parseGitHub(json: String): UpdateInfo {
        val obj = JSONObject(json)
        val tagName = obj.optString("tag_name", "").trimStart('v')
        val body = obj.optString("body", "")

        val assets = obj.optJSONArray("assets")
        val downloadUrl = assets?.takeIf { it.length() > 0 }?.let {
            it.getJSONObject(0).optString("browser_download_url", "")
        } ?: throw Exception("GitHub: no asset found")

        val versionCode = tagName.toIntOrNull() ?: parseVersionCode(tagName)

        return UpdateInfo(
            tagName = tagName,
            versionCode = versionCode,
            versionName = tagName,
            downloadUrl = downloadUrl,
            body = stripMarkdown(body)
        )
    }

    /**
     * 移除 Markdown 格式符号，用于纯文本弹窗显示
     */
    private fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("""[*#]{1,3}\s?"""), "")
            .replace(Regex("""^-\s""", RegexOption.MULTILINE), "")
            .replace("\\n", "\n")
            .replace(Regex("""[`>]"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    /**
     * 从 1.0 / 1.0.0 格式解析 versionCode
     */
    private fun parseVersionCode(versionName: String): Int {
        val parts = versionName.split(".")
        return when (parts.size) {
            1 -> parts[0].toIntOrNull() ?: 0
            2 -> (parts[0].toIntOrNull() ?: 0) * 100 + (parts[1].toIntOrNull() ?: 0)
            else -> (parts[0].toIntOrNull() ?: 0) * 10000 + (parts[1].toIntOrNull() ?: 0) * 100 + (parts[2].toIntOrNull() ?: 0)
        }
    }

    /**
     * 下载 APK 并显示通知进度，完成后拉起安装
     */
    suspend fun downloadAndInstall(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(nm)

        val apkFile = File(context.externalCacheDir, "update.apk")
        apkFile.delete()

        try {
            val connection = URL(info.downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }

            val totalSize = connection.contentLength.toLong()
            val input = connection.inputStream
            val output = FileOutputStream(apkFile)

            var downloaded = 0L
            val buffer = ByteArray(8192)
            var lastProgress = 0

            input.use { inputStream ->
                output.use { outputStream ->
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        if (totalSize > 0) {
                            val progress = (downloaded * 100 / totalSize).toInt()
                            if (progress > lastProgress + 5 || progress == 100) {
                                lastProgress = progress
                                showProgressNotification(context, nm, progress)
                            }
                        }
                    }
                }
            }
            connection.disconnect()

            // 下载完成
            showInstallNotification(context, nm, apkFile)
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            showFailedNotification(context, nm)
        }
    }

    private fun createNotificationChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "下载更新",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(context: Context, nm: NotificationManager, progress: Int) {
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_downloading))
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showInstallNotification(context: Context, nm: NotificationManager, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileProvider", apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_download_success))
            .setContentText("v${BuildConfig.VERSION_NAME} → v${apkFile.name}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        nm.notify(NOTIFICATION_ID, builder.build())

        // 直接拉起安装
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "无法启动安装", e)
        }
    }

    private fun showFailedNotification(context: Context, nm: NotificationManager) {
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.update_download_failed))
            .setContentText("请稍后重试")
            .setAutoCancel(true)
        nm.notify(NOTIFICATION_ID, builder.build())
    }
}
