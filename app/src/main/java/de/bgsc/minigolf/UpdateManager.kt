package de.bgsc.minigolf

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.annotations.SerializedName
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("name") val name: String
)

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient()
    private val repoUrl = "https://api.github.com/repos/bloodwick3d/BGSC-Punktekarte/releases/latest"

    fun checkForUpdates(
        currentVersion: String, 
        onUpdateAvailable: (String, String, String?) -> Unit,
        onNoUpdate: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val request = Request.Builder()
            .url(repoUrl)
            .header("User-Agent", "MiniGolf-Score-App")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UpdateManager", "Netzwerkfehler: ${e.message}")
                onError(e.message ?: "Unbekannter Netzwerkfehler")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                val responseBody = body.string()
                
                if (!response.isSuccessful) {
                    Log.e("UpdateManager", "GitHub Fehler: ${response.code}")
                    onError("GitHub Fehler: ${response.code}")
                    return
                }

                responseBody.let { json ->
                    try {
                        val release = com.google.gson.Gson().fromJson(json, GitHubRelease::class.java)
                        val latestClean = release.tagName.lowercase().removePrefix("v").split("-")[0].trim()
                        val currentClean = currentVersion.lowercase().removePrefix("v").split("-")[0].trim()
                        
                        Log.i("UpdateManager", "VERGLEICH: Lokal [$currentClean] | GitHub [$latestClean]")
                        
                        if (latestClean == currentClean) {
                            onNoUpdate()
                            return
                        }
                        
                        if (isNewerVersion(currentClean, latestClean)) {
                            val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                            if (apkAsset != null) {
                                onUpdateAvailable(latestClean, apkAsset.downloadUrl, release.body)
                            } else {
                                onNoUpdate()
                            }
                        } else {
                            onNoUpdate()
                        }
                    } catch (e: Exception) {
                        Log.e("UpdateManager", "Fehler bei Versionsprüfung: ${e.message}")
                        onError("Datenfehler")
                    }
                }
            }
        })
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

    fun downloadAndInstallApk(url: String, onProgress: (Float) -> Unit) {
        val request = Request.Builder().url(url).header("User-Agent", "MiniGolf-Score-App").build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { Log.e("UpdateManager", "Download Fehler: ${e.message}") }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                val updateDir = File(context.cacheDir, "updates")
                if (!updateDir.exists()) updateDir.mkdirs()
                val file = File(updateDir, "update.apk")
                
                val totalBytes = body.contentLength()
                try {
                    body.byteStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var downloadedBytes = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) onProgress(downloadedBytes.toFloat() / totalBytes)
                            }
                        }
                    }
                    installApk(file)
                } catch (e: Exception) { Log.e("UpdateManager", "Speicherfehler: ${e.message}") }
            }
        })
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
