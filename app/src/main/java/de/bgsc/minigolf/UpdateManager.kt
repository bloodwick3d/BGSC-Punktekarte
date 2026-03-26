package de.bgsc.minigolf

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.FileOutputStream

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("name") val name: String
)

sealed class UpdateResult {
    data class NewVersion(val version: String, val url: String, val notes: String?) : UpdateResult()
    data object NoUpdate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val repoUrl = "https://api.github.com/repos/bloodwick3d/BGSC-Punktekarte/releases/latest"

    suspend fun checkForUpdates(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(repoUrl)
            .header("User-Agent", "MiniGolf-Score-App")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                return@withContext UpdateResult.Error("GitHub Fehler: ${response.code}")
            }

            val release = Gson().fromJson(responseBody, GitHubRelease::class.java)
            val latestClean = release.tagName.lowercase().removePrefix("v").split("-")[0].trim()
            val currentClean = currentVersion.lowercase().removePrefix("v").split("-")[0].trim()

            Log.i("UpdateManager", "VERGLEICH: Lokal [$currentClean] | GitHub [$latestClean]")

            if (isNewerVersion(currentClean, latestClean)) {
                val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    UpdateResult.NewVersion(latestClean, apkAsset.downloadUrl, release.body)
                } else {
                    UpdateResult.NoUpdate
                }
            } else {
                UpdateResult.NoUpdate
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Fehler bei Versionsprüfung: ${e.message}")
            UpdateResult.Error(e.message ?: "Unbekannter Fehler")
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        if (current == latest) return false
        return try {
            val currentParts = current.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
            val length = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until length) {
                val curr = currentParts.getOrNull(i) ?: 0
                val late = latestParts.getOrNull(i) ?: 0
                if (late > curr) return true
                if (late < curr) return false
            }
            false
        } catch (_: Exception) { false }
    }

    suspend fun downloadAndInstallApk(url: String, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "MiniGolf-Score-App").build()
        try {
            val response = client.newCall(request).execute()
            val body = response.body
            
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()
            val file = File(updateDir, "update.apk")
            
            val totalBytes = body.contentLength()
            body.byteStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var downloadedBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) { installApk(file) }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Download/Installation fehlgeschlagen: ${e.message}")
        }
    }

    private fun installApk(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            Log.i("UpdateManager", "Starte Installation für URI: $uri")
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Installation fehlgeschlagen: ${e.message}", e)
        }
    }
}
