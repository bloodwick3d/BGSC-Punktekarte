package de.bgsc.minigolf

import android.util.Base64
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.webkit.JavaScriptReplyProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

internal class LegacyMigrationCoordinator(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val reply: (JavaScriptReplyProxy, String, Boolean, String, JSONObject?) -> Unit
) {
    private val reader = LegacyDatabaseReader(activity)
    private val running = AtomicBoolean(false)
    @Volatile private var pendingExpected: JSONObject? = null
    private val mode: String get() = if (BuildConfig.DEBUG) "demo" else "production"

    fun sendInfo(requestId: String, proxy: JavaScriptReplyProxy) {
        activity.lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { reader.inspect(mode).toJson() } }
            result.onSuccess { reply(proxy, requestId, true, "Migrationsstatus geladen.", it) }
                .onFailure { reply(proxy, requestId, false, it.message ?: "Migrationsstatus konnte nicht geladen werden.", null) }
        }
    }

    fun start(requestId: String, proxy: JavaScriptReplyProxy) {
        if (!running.compareAndSet(false, true)) {
            reply(proxy, requestId, false, "Eine Migration läuft bereits.", null)
            return
        }

        val sessionId = UUID.randomUUID().toString()
        reply(proxy, requestId, true, "Migration gestartet.", JSONObject().put("sessionId", sessionId))

        activity.lifecycleScope.launch {
            try {
                sendStatus(1, "active", "Alte Daten suchen", "Datenbank und Version werden geprüft …", 4)
                val payloadResult = withContext(Dispatchers.IO) {
                    reader.read(mode) { step, detail, percent ->
                        postStatus(step, "active", stepTitle(step), detail, percent)
                    }
                }
                pendingExpected = payloadResult.payload.optJSONObject("expected")
                sendStatus(5, "active", "In die PWA übertragen", "Datenpaket wird vorbereitet …", 58)
                sendPayload(sessionId, payloadResult.payload)
            } catch (error: Throwable) {
                pendingExpected = null
                sendStatus(0, "error", "Migration fehlgeschlagen", error.message ?: "Unbekannter Fehler", 0)
                running.set(false)
            }
        }
    }

    fun acceptResult(requestId: String, reportText: String, proxy: JavaScriptReplyProxy) {
        val report = runCatching { JSONObject(reportText) }.getOrElse {
            running.set(false)
            reply(proxy, requestId, false, "Ungültiger Prüfbericht aus der PWA.", null)
            return
        }
        val success = report.optBoolean("success", false)
        val expected = pendingExpected
        val imported = report.optJSONObject("imported")
        val countsMatch = expected != null && imported != null &&
            imported.optInt("activeGames", -1) == expected.optInt("activeGames", -2) &&
            imported.optInt("endedGames", -1) == expected.optInt("endedGames", -2) &&
            imported.optInt("tournamentNotes", -1) == expected.optInt("tournamentNotes", -2) &&
            imported.optInt("media", -1) == expected.optInt("media", -2)

        if (success && countsMatch) {
            reader.markNativeComplete(report)
            pendingExpected = null
            running.set(false)
            reply(proxy, requestId, true, "Migration wurde nativ bestätigt.", report)
        } else {
            pendingExpected = null
            running.set(false)
            val message = if (success && !countsMatch) {
                "Die native Rückprüfung der Datensatzanzahlen ist fehlgeschlagen."
            } else report.optString("message", "Die PWA-Prüfung ist fehlgeschlagen.")
            reply(proxy, requestId, false, message, report)
        }
    }

    private suspend fun sendPayload(sessionId: String, payload: JSONObject) {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val hash = sha256(bytes)
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val chunkSize = 48_000
        val chunks = (base64.length + chunkSize - 1) / chunkSize

        evaluate(
            "window.MiniGolfMigration && window.MiniGolfMigration.begin(" +
                "${JSONObject.quote(sessionId)},$chunks,${bytes.size},${JSONObject.quote(hash)});"
        )

        for (index in 0 until chunks) {
            val start = index * chunkSize
            val end = minOf(base64.length, start + chunkSize)
            val chunk = base64.substring(start, end)
            evaluate(
                "window.MiniGolfMigration && window.MiniGolfMigration.receiveChunk(" +
                    "${JSONObject.quote(sessionId)},$index,$chunks,${JSONObject.quote(chunk)});"
            )
            val percent = 60 + ((index + 1) * 23 / chunks.coerceAtLeast(1))
            sendStatus(5, "active", "In die PWA übertragen", "Paket ${index + 1} von $chunks übertragen …", percent)
            if (index % 8 == 0) delay(8)
        }

        sendStatus(6, "active", "Daten prüfen", "PWA importiert und kontrolliert Datensätze und Bilder …", 86)
        evaluate(
            "window.MiniGolfMigration && window.MiniGolfMigration.commit(" +
                "${JSONObject.quote(sessionId)},${JSONObject.quote(hash)});"
        )
    }

    private fun statusJson(step: Int, state: String, title: String, detail: String, percent: Int): JSONObject =
        JSONObject()
            .put("step", step)
            .put("state", state)
            .put("title", title)
            .put("detail", detail)
            .put("percent", percent.coerceIn(0, 100))

    private fun postStatus(step: Int, state: String, title: String, detail: String, percent: Int) {
        val json = statusJson(step, state, title, detail, percent)
        webView.post {
            webView.evaluateJavascript(
                "window.MiniGolfMigration && window.MiniGolfMigration.nativeStatus(${json});",
                null
            )
        }
    }

    private suspend fun sendStatus(step: Int, state: String, title: String, detail: String, percent: Int) {
        val json = statusJson(step, state, title, detail, percent)
        evaluate("window.MiniGolfMigration && window.MiniGolfMigration.nativeStatus(${json});")
    }

    private suspend fun evaluate(script: String): String? = suspendCancellableCoroutine { continuation ->
        webView.post {
            if (!continuation.isActive) return@post
            webView.evaluateJavascript(script) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }
    }

    private fun stepTitle(step: Int): String = when (step) {
        1 -> "Alte Daten suchen"
        2 -> "Spiele lesen"
        3 -> "Turniernotizen lesen"
        4 -> "Bilder vorbereiten"
        5 -> "In die PWA übertragen"
        6 -> "Daten prüfen"
        else -> "Migration"
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
