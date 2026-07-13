package de.bgsc.minigolf

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale

class MainActivity : androidx.activity.ComponentActivity() {

    companion object {
        private const val TAG = "MiniGolfWrapper"
        private const val APP_ORIGIN = "https://appassets.androidplatform.net"
        private const val START_URL = "$APP_ORIGIN/assets/pwa/index.html"
        private const val MAX_NATIVE_TRANSFER_BYTES = 24 * 1024 * 1024
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorPanel: View
    private lateinit var errorText: TextView
    private lateinit var migrationCoordinator: LegacyMigrationCoordinator

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var cameraOutputUri: Uri? = null
    private var pendingSave: PendingSave? = null

    private data class NativePacket(
        val requestId: String,
        val action: String,
        val fileName: String,
        val mimeType: String,
        val title: String,
        val text: String,
        val bytes: ByteArray?,
        val json: JSONObject
    )

    private data class PendingSave(
        val packet: NativePacket,
        val replyProxy: JavaScriptReplyProxy
    )

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback ?: return@registerForActivityResult
        fileChooserCallback = null

        val uris = when {
            result.resultCode != Activity.RESULT_OK -> null
            result.data?.data != null || result.data?.clipData != null ->
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            cameraOutputUri != null -> arrayOf(cameraOutputUri!!)
            else -> null
        }
        cameraOutputUri = null
        callback.onReceiveValue(uris)
    }

    private val saveDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pending = pendingSave ?: return@registerForActivityResult
        pendingSave = null
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) {
            reply(pending.replyProxy, pending.packet.requestId, false, "Speichern abgebrochen.")
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        output.write(pending.packet.bytes ?: ByteArray(0))
                        output.flush()
                    } ?: error("Datei konnte nicht geöffnet werden.")
                }.exceptionOrNull()
            }

            if (error == null) {
                Toast.makeText(this@MainActivity, "Datei gespeichert", Toast.LENGTH_SHORT).show()
                reply(pending.replyProxy, pending.packet.requestId, true, "Datei gespeichert.")
            } else {
                reply(pending.replyProxy, pending.packet.requestId, false, error.message ?: "Speichern fehlgeschlagen.")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        errorPanel = findViewById(R.id.errorPanel)
        errorText = findViewById(R.id.errorText)
        migrationCoordinator = LegacyMigrationCoordinator(this, webView, ::reply)
        findViewById<Button>(R.id.reloadButton).setOnClickListener {
            errorPanel.isVisible = false
            webView.reload()
        }

        configureWebView()
        configureBackNavigation()
        cleanupOldShareFiles()

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(START_URL)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        WebView.setWebContentsDebuggingEnabled(true)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            userAgentString = "$userAgentString MiniGolfAndroidWrapper/0.3-pwa"
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            androidx.webkit.WebSettingsCompat.setSafeBrowsingEnabled(webView.settings, true)
        }

        configureNativeMessageBridge()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            @Suppress("DEPRECATION")
            override fun shouldInterceptRequest(view: WebView?, url: String): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(Uri.parse(url))
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val uri = request.url
                val host = uri.host.orEmpty().lowercase(Locale.ROOT)

                if (host == "appassets.androidplatform.net") return false
                if (host == "script.google.com" || host == "script.googleusercontent.com" || host.endsWith(".googleusercontent.com")) {
                    return false
                }

                if (!request.isForMainFrame) return false
                return openExternal(uri)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                val uri = Uri.parse(url)
                val host = uri.host.orEmpty().lowercase(Locale.ROOT)
                if (host == "appassets.androidplatform.net" || host == "script.google.com" || host.endsWith(".googleusercontent.com")) {
                    return false
                }
                return openExternal(uri)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.isVisible = true
                errorPanel.isVisible = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (progressBar.progress >= 100) progressBar.isVisible = false
                injectShareShim()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    errorText.text = "Die lokale MiniGolf-App konnte nicht geladen werden.\n\n${error.description}"
                    errorPanel.isVisible = true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.isVisible = newProgress < 100
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(TAG, "JS ${consoleMessage.messageLevel()}: ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                this@MainActivity.fileChooserCallback = filePathCallback
                launchFileChooser(fileChooserParams)
                return true
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                Toast.makeText(this, "Export wird vorbereitet …", Toast.LENGTH_SHORT).show()
            } else {
                openExternal(Uri.parse(url))
            }
        }
    }

    private fun configureNativeMessageBridge() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            Toast.makeText(this, "Bitte Android System WebView aktualisieren.", Toast.LENGTH_LONG).show()
            return
        }

        WebViewCompat.addWebMessageListener(
            webView,
            "MiniGolfNative",
            setOf(APP_ORIGIN)
        ) { _, message, sourceOrigin, isMainFrame, replyProxy ->
            val trustedOrigin = sourceOrigin.scheme == "https" && sourceOrigin.host == "appassets.androidplatform.net"
            if (!isMainFrame || !trustedOrigin) return@addWebMessageListener
            lifecycleScope.launch {
                val packet = runCatching {
                    withContext(Dispatchers.Default) { decodeNativePacket(message) }
                }.getOrElse { error ->
                    reply(replyProxy, "", false, error.message ?: "Ungültige Android-Anfrage.")
                    return@launch
                }
                handleNativePacket(packet, replyProxy)
            }
        }
    }

    private fun decodeNativePacket(message: WebMessageCompat): NativePacket {
        return when (message.type) {
            WebMessageCompat.TYPE_ARRAY_BUFFER -> {
                val packet = message.arrayBuffer
                require(packet.size >= 4) { "Binärpaket ist zu kurz." }
                val headerLength = ByteBuffer.wrap(packet, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                require(headerLength in 2..65536 && 4 + headerLength <= packet.size) { "Ungültiger Paketkopf." }
                val header = JSONObject(String(packet, 4, headerLength, StandardCharsets.UTF_8))
                val bytes = packet.copyOfRange(4 + headerLength, packet.size)
                packetFromJson(header, bytes)
            }
            else -> {
                val json = JSONObject(message.data ?: "{}")
                val bytes = json.optString("dataBase64").takeIf { it.isNotBlank() }?.let {
                    Base64.decode(it, Base64.DEFAULT)
                }
                packetFromJson(json, bytes)
            }
        }
    }

    private fun packetFromJson(json: JSONObject, bytes: ByteArray?): NativePacket {
        require(bytes == null || bytes.size <= MAX_NATIVE_TRANSFER_BYTES) { "Datei ist größer als 24 MB." }
        val action = json.optString("action").take(40)
        val textLimit = if (action == "migrationResult") 500_000 else 5_000
        return NativePacket(
            requestId = json.optString("requestId").take(160),
            action = action,
            fileName = sanitizeFileName(json.optString("fileName", "MiniGolf_Datei")),
            mimeType = json.optString("mimeType", "application/octet-stream").take(120),
            title = json.optString("title", "MiniGolf teilen").take(200),
            text = json.optString("text").take(textLimit),
            bytes = bytes,
            json = json
        )
    }

    private fun handleNativePacket(packet: NativePacket, replyProxy: JavaScriptReplyProxy) {
        when (packet.action) {
            "saveFile" -> startSaveFile(packet, replyProxy)
            "shareFile" -> shareFile(packet, replyProxy)
            "shareText" -> shareText(packet, replyProxy)
            "migrationInfo" -> migrationCoordinator.sendInfo(packet.requestId, replyProxy)
            "startMigration" -> migrationCoordinator.start(packet.requestId, replyProxy)
            "migrationResult" -> migrationCoordinator.acceptResult(packet.requestId, packet.text, replyProxy)
            "setKeepScreenOn" -> setKeepScreenOn(packet, replyProxy)
            else -> reply(replyProxy, packet.requestId, false, "Unbekannte Android-Aktion.")
        }
    }

    private fun setKeepScreenOn(packet: NativePacket, replyProxy: JavaScriptReplyProxy) {
        val enabled = packet.json.optBoolean("enabled", false)
        runOnUiThread {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        reply(replyProxy, packet.requestId, true, "KeepScreenOn auf $enabled gesetzt.")
    }

    private fun startSaveFile(packet: NativePacket, replyProxy: JavaScriptReplyProxy) {
        if (packet.bytes == null) {
            reply(replyProxy, packet.requestId, false, "Dateidaten fehlen.")
            return
        }
        if (pendingSave != null) {
            reply(replyProxy, packet.requestId, false, "Ein Speichervorgang läuft bereits.")
            return
        }
        pendingSave = PendingSave(packet, replyProxy)
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = packet.mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_TITLE, packet.fileName)
        }
        saveDocumentLauncher.launch(intent)
    }

    private fun shareFile(packet: NativePacket, replyProxy: JavaScriptReplyProxy) {
        val bytes = packet.bytes
        if (bytes == null) {
            reply(replyProxy, packet.requestId, false, "Dateidaten fehlen.")
            return
        }

        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) {
                val folder = File(cacheDir, "shares").apply { mkdirs() }
                val target = File(folder, packet.fileName)
                target.writeBytes(bytes)
                FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", target)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = packet.mimeType.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_STREAM, uri)
                if (packet.text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, packet.text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(packet.fileName, uri)
            }
            startActivity(Intent.createChooser(intent, packet.title.ifBlank { "MiniGolf teilen" }))
            reply(replyProxy, packet.requestId, true, "Teilen geöffnet.")
        }
    }

    private fun shareText(packet: NativePacket, replyProxy: JavaScriptReplyProxy) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, packet.title)
            putExtra(Intent.EXTRA_TEXT, packet.text)
        }
        startActivity(Intent.createChooser(intent, packet.title.ifBlank { "MiniGolf teilen" }))
        reply(replyProxy, packet.requestId, true, "Teilen geöffnet.")
    }

    private fun reply(
        proxy: JavaScriptReplyProxy,
        requestId: String,
        ok: Boolean,
        message: String,
        data: JSONObject? = null
    ) {
        val response = JSONObject()
            .put("requestId", requestId)
            .put("ok", ok)
            .put("message", message)
        if (data != null) response.put("data", data)
        proxy.postMessage(response.toString())
    }

    private fun launchFileChooser(params: WebChromeClient.FileChooserParams) {
        val accepts = params.acceptTypes.map { it.lowercase(Locale.ROOT) }
        val acceptsImages = accepts.isEmpty() || accepts.any { it.isBlank() || it == "*/*" || it.startsWith("image/") }
        val cameraIntent = if (acceptsImages) createCameraIntent() else null

        if (params.isCaptureEnabled && cameraIntent != null) {
            fileChooserLauncher.launch(cameraIntent)
            return
        }

        val contentIntent = runCatching { params.createIntent() }.getOrElse {
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = accepts.firstOrNull { it.contains('/') && it != "*/*" } ?: "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
            }
        }

        val chooser = Intent.createChooser(contentIntent, "Datei wählen")
        if (cameraIntent != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
        fileChooserLauncher.launch(chooser)
    }

    private fun createCameraIntent(): Intent? {
        val folder = File(cacheDir, "camera").apply { mkdirs() }
        val file = File.createTempFile("minigolf_", ".jpg", folder)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("MiniGolf Foto", uri)
        }
        return if (intent.resolveActivity(packageManager) != null) {
            cameraOutputUri = uri
            intent
        } else {
            file.delete()
            null
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        return runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        }.getOrElse {
            Toast.makeText(this, "Link konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show()
            true
        }
    }

    private fun configureBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    private fun cleanupOldShareFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
            File(cacheDir, "shares").listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        }
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim()
        return cleaned.take(180).ifBlank { "MiniGolf_Datei" }
    }

    private fun injectShareShim() {
        webView.evaluateJavascript("""
            (function() {
                if (navigator.share && !navigator._nativeShare) {
                    navigator._nativeShare = navigator.share;
                }
                navigator.share = function(data) {
                    if (!data) return Promise.reject(new Error('No data to share'));
                    return new Promise((resolve, reject) => {
                        const requestId = 'share_' + Date.now();
                        const packet = {
                            requestId: requestId,
                            action: 'shareFile',
                            title: data.title || '',
                            text: data.text || '',
                            fileName: 'MiniGolf_Export.mgpk',
                            mimeType: 'application/octet-stream'
                        };
                        
                        if (data.files && data.files.length > 0) {
                            const file = data.files[0];
                            packet.fileName = file.name || 'MiniGolf_Export.mgpk';
                            const reader = new FileReader();
                            reader.onload = function() {
                                packet.dataBase64 = reader.result.split(',')[1];
                                window.MiniGolfNative.postMessage(JSON.stringify(packet));
                                resolve();
                            };
                            reader.onerror = () => reject(new Error('File reading failed'));
                            reader.readAsDataURL(file);
                        } else {
                            packet.action = 'shareText';
                            window.MiniGolfNative.postMessage(JSON.stringify(packet));
                            resolve();
                        }
                    });
                };
                if (!navigator.canShare) {
                    navigator.canShare = () => true;
                }
            })();
        """.trimIndent(), null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (isFinishing) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }
}
