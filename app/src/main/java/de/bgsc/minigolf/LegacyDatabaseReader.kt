package de.bgsc.minigolf

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

internal data class LegacyInspection(
    val mode: String,
    val databaseExists: Boolean,
    val databaseVersion: Int,
    val activeGames: Int,
    val endedGames: Int,
    val tournamentNotes: Int,
    val nativeCompleted: Boolean
) {
    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode)
        .put("databaseExists", databaseExists)
        .put("databaseVersion", databaseVersion)
        .put("activeGames", activeGames)
        .put("endedGames", endedGames)
        .put("tournamentNotes", tournamentNotes)
        .put("nativeCompleted", nativeCompleted)
        .put("hasLegacyData", activeGames + endedGames + tournamentNotes > 0)
}

internal data class LegacyPayloadResult(
    val payload: JSONObject,
    val warnings: List<String>
)

internal class LegacyDatabaseReader(private val context: Context) {
    companion object {
        const val DATABASE_NAME = "minigolf_database"
        private const val MAX_IMAGE_BYTES = 24L * 1024L * 1024L
        private const val MAX_TOTAL_MEDIA_BYTES = 48L * 1024L * 1024L
    }

    private val preferences = context.getSharedPreferences("minigolf_native_migration", Context.MODE_PRIVATE)

    fun inspect(mode: String): LegacyInspection {
        if (mode == "demo") {
            return LegacyInspection(
                mode = mode,
                databaseExists = true,
                databaseVersion = 9,
                activeGames = 1,
                endedGames = 2,
                tournamentNotes = 1,
                nativeCompleted = preferences.getBoolean("migration_v1_completed", false)
            )
        }

        val file = context.getDatabasePath(DATABASE_NAME)
        if (!file.exists()) {
            return LegacyInspection(
                mode = mode,
                databaseExists = false,
                databaseVersion = 0,
                activeGames = 0,
                endedGames = 0,
                tournamentNotes = 0,
                nativeCompleted = preferences.getBoolean("migration_v1_completed", false)
            )
        }

        return openReadOnly(file).use { db ->
            val version = db.version
            val gameColumns = tableColumns(db, "game_results")
            val active = if (gameColumns.contains("isCompleted")) countWhere(db, "game_results", "isCompleted = 0") else 0
            val ended = if (gameColumns.contains("isCompleted")) countWhere(db, "game_results", "isCompleted = 1") else countWhere(db, "game_results", null)
            val notes = countWhere(db, "tournament_note_results", null)
            LegacyInspection(
                mode = mode,
                databaseExists = true,
                databaseVersion = version,
                activeGames = active,
                endedGames = ended,
                tournamentNotes = notes,
                nativeCompleted = preferences.getBoolean("migration_v1_completed", false)
            )
        }
    }

    fun read(mode: String, onProgress: (step: Int, detail: String, percent: Int) -> Unit): LegacyPayloadResult {
        if (mode == "demo") return createDemoPayload(onProgress)

        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        require(databaseFile.exists()) { "Die alte Datenbank wurde nicht gefunden." }

        val warnings = mutableListOf<String>()
        onProgress(1, "Sicherheitskopie der alten Datenbank wird erstellt …", 5)
        backupDatabaseFiles(databaseFile, warnings)
        val games = JSONArray()
        val notes = JSONArray()
        val media = JSONArray()
        val mediaBySource = linkedMapOf<String, JSONObject>()
        val mediaBudget = MediaBudget(MAX_TOTAL_MEDIA_BYTES)
        var activeCount = 0
        var endedCount = 0
        var missingImages = 0

        onProgress(1, "Alte SQLite-Datenbank wird geöffnet …", 8)
        openReadOnly(databaseFile).use { db ->
            val gameColumns = tableColumns(db, "game_results")
            if (gameColumns.isNotEmpty()) {
                onProgress(2, "Gespeicherte Spiele werden gelesen …", 18)
                val projection = listOf("id", "date", "system", "location", "playersJson", "isFullGame", "isCompleted", "hasStats")
                    .filter { gameColumns.contains(it) }
                    .joinToString(",")
                db.rawQuery("SELECT $projection FROM game_results ORDER BY date ASC", null).use { cursor ->
                    var index = 0
                    while (cursor.moveToNext()) {
                        val isCompleted = cursor.intOr("isCompleted", 1) != 0
                        val game = convertGame(cursor, isCompleted, warnings)
                        games.put(game)
                        if (isCompleted) endedCount++ else activeCount++
                        index++
                        if (index % 10 == 0) {
                            onProgress(2, "$index Spiele gelesen …", 18 + (index.coerceAtMost(100) / 10))
                        }
                    }
                }
            }

            val noteColumns = tableColumns(db, "tournament_note_results")
            if (noteColumns.isNotEmpty()) {
                onProgress(3, "Turniernotizen werden gelesen …", 34)
                val projection = listOf("id", "date", "location", "system", "notesJson")
                    .filter { noteColumns.contains(it) }
                    .joinToString(",")
                db.rawQuery("SELECT $projection FROM tournament_note_results ORDER BY date ASC", null).use { cursor ->
                    var noteIndex = 0
                    while (cursor.moveToNext()) {
                        val nativeId = cursor.longOr("id", noteIndex.toLong() + 1L)
                        val holesSource = parseArray(cursor.stringOr("notesJson", "[]"), warnings, "Notiz $nativeId")
                        val holes = JSONArray()
                        for (holeIndex in 0 until 18) {
                            val source = holesSource.optJSONObject(holeIndex) ?: JSONObject()
                            val convertedImages = JSONArray()
                            val allImages = mutableListOf<JSONObject>()
                            val sourceImages = source.optJSONArray("images")
                            if (sourceImages != null) {
                                for (imageIndex in 0 until sourceImages.length()) {
                                    sourceImages.optJSONObject(imageIndex)?.let(allImages::add)
                                }
                            }
                            val legacyPath = source.optString("imagePath").takeIf { it.isNotBlank() }
                            val legacyOriginal = source.optString("originalImagePath").takeIf { it.isNotBlank() }
                            if (legacyPath != null || legacyOriginal != null) {
                                allImages.add(JSONObject().apply {
                                    if (legacyPath != null) put("imagePath", legacyPath)
                                    if (legacyOriginal != null) put("originalImagePath", legacyOriginal)
                                })
                            }

                            allImages.forEachIndexed { imageIndex, image ->
                                val currentPath = image.optString("imagePath").takeIf { it.isNotBlank() }
                                val originalPath = image.optString("originalImagePath").takeIf { it.isNotBlank() }
                                val embedded = image.optString("imageData").takeIf { it.isNotBlank() }

                                val originalId = when {
                                    originalPath != null -> addMediaFile(originalPath, mediaBySource, warnings) { bytes -> mediaBudget.reserve(bytes.size) }
                                    embedded != null -> addEmbeddedMedia(embedded, mediaBySource, warnings) { bytes -> mediaBudget.reserve(bytes.size) }
                                    else -> null
                                }
                                val editedId = when {
                                    currentPath != null -> addMediaFile(currentPath, mediaBySource, warnings) { bytes -> mediaBudget.reserve(bytes.size) }
                                    embedded != null -> addEmbeddedMedia(embedded, mediaBySource, warnings) { bytes -> mediaBudget.reserve(bytes.size) }
                                    else -> null
                                }
                                val resolvedOriginal = originalId ?: editedId
                                val resolvedEdited = editedId ?: originalId
                                if (resolvedOriginal == null && resolvedEdited == null) {
                                    missingImages++
                                } else {
                                    convertedImages.put(JSONObject()
                                        .put("id", "native-image-$nativeId-$holeIndex-$imageIndex")
                                        .put("originalId", resolvedOriginal ?: JSONObject.NULL)
                                        .put("editedId", resolvedEdited ?: JSONObject.NULL)
                                        .put("createdAt", cursor.longOr("date", System.currentTimeMillis())))
                                }
                            }

                            holes.put(JSONObject()
                                .put("ball", source.optString("ball"))
                                .put("start", source.optString("start", source.optString("startPoint")))
                                .put("notes", source.optString("notes"))
                                .put("images", convertedImages))
                        }

                        notes.put(JSONObject()
                            .put("id", "native-note-$nativeId")
                            .put("legacyNativeId", "note:$nativeId")
                            .put("date", iso(cursor.longOr("date", System.currentTimeMillis())))
                            .put("location", cursor.stringOr("location", ""))
                            .put("system", cursor.stringOr("system", "Miniaturgolf\n(Eternit)"))
                            .put("holes", holes))
                        noteIndex++
                        onProgress(3, "$noteIndex Turniernotizen gelesen …", 34 + noteIndex.coerceAtMost(10))
                    }
                }
            }

            onProgress(4, "Bilder und Prüfsummen werden vorbereitet …", 48)
            mediaBySource.values.forEach(media::put)

            val payload = JSONObject()
                .put("format", "minigolf-native-migration")
                .put("version", 1)
                .put("createdAt", Instant.now().toString())
                .put("sourceDatabaseVersion", db.version)
                .put("games", games)
                .put("tournamentNotes", notes)
                .put("media", media)
                .put("settings", readLegacySettings(warnings))
                .put("expected", JSONObject()
                    .put("activeGames", activeCount)
                    .put("endedGames", endedCount)
                    .put("tournamentNotes", notes.length())
                    .put("media", media.length())
                    .put("missingImages", missingImages))
                .put("warnings", JSONArray(warnings))

            return LegacyPayloadResult(payload, warnings)
        }
    }

    fun markNativeComplete(report: JSONObject) {
        preferences.edit()
            .putBoolean("migration_v1_completed", true)
            .putString("migration_v1_report", report.toString())
            .putLong("migration_v1_completed_at", System.currentTimeMillis())
            .apply()
    }

    private fun createDemoPayload(onProgress: (Int, String, Int) -> Unit): LegacyPayloadResult {
        val warnings = mutableListOf<String>()
        onProgress(1, "Demo-Daten werden vorbereitet …", 10)

        fun player(name: String, color: String, scores: List<List<Int?>>): JSONObject {
            val rounds = JSONArray()
            scores.forEach { row -> rounds.put(JSONArray(row.map { it ?: JSONObject.NULL })) }
            return JSONObject()
                .put("name", name)
                .put("color", color)
                .put("roundScores", rounds)
        }

        val games = JSONArray()
            .put(JSONObject()
                .put("id", "native-game-demo-active")
                .put("legacyNativeId", "game:demo-active")
                .put("date", Instant.now().minusSeconds(3600).toString())
                .put("reason", "Migriert: aktives Spiel")
                .put("system", "Miniaturgolf\n(Eternit)")
                .put("location", "Demo-Anlage")
                .put("hasStats", false)
                .put("isCompleted", false)
                .put("players", JSONArray().put(player("Patrick", "#B71C2A", listOf(listOf(2, 3, 2, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null)))))
                .put("total", JSONArray().put(JSONObject().put("name", "Patrick").put("total", 7))))
            .put(JSONObject()
                .put("id", "native-game-demo-ended-1")
                .put("legacyNativeId", "game:demo-ended-1")
                .put("date", Instant.now().minusSeconds(86400).toString())
                .put("reason", "Migriert: beendetes Spiel")
                .put("system", "Minigolf\n(Beton)")
                .put("location", "Testplatz")
                .put("hasStats", true)
                .put("isCompleted", true)
                .put("players", JSONArray()
                    .put(player("Patrick", "#B71C2A", listOf(List(18) { if (it % 5 == 0) 3 else 2 })))
                    .put(player("Alex", "#2735A3", listOf(List(18) { if (it % 4 == 0) 1 else 2 }))))
                .put("total", JSONArray()
                    .put(JSONObject().put("name", "Patrick").put("total", 42))
                    .put(JSONObject().put("name", "Alex").put("total", 31))))
            .put(JSONObject()
                .put("id", "native-game-demo-ended-2")
                .put("legacyNativeId", "game:demo-ended-2")
                .put("date", Instant.now().minusSeconds(172800).toString())
                .put("reason", "Migriert: beendetes Spiel")
                .put("system", "Filzgolf")
                .put("location", "Demo-Halle")
                .put("hasStats", false)
                .put("isCompleted", true)
                .put("players", JSONArray().put(player("Spieler 1", "#4CAF50", listOf(List(18) { 2 }))))
                .put("total", JSONArray().put(JSONObject().put("name", "Spieler 1").put("total", 36))))

        onProgress(3, "Demo-Turniernotiz wird erstellt …", 36)
        val mediaBytes = createDemoImage()
        val mediaId = "native-media-demo-image"
        val media = JSONArray().put(JSONObject()
            .put("id", mediaId)
            .put("mimeType", "image/png")
            .put("size", mediaBytes.size)
            .put("sha256", sha256(mediaBytes))
            .put("base64", Base64.encodeToString(mediaBytes, Base64.NO_WRAP)))

        val holes = JSONArray()
        repeat(18) { index ->
            holes.put(JSONObject()
                .put("ball", if (index == 0) "Rot" else "")
                .put("start", if (index == 0) "Links" else "")
                .put("notes", if (index == 0) "Demo-Migration mit Bild" else "")
                .put("images", if (index == 0) JSONArray().put(JSONObject()
                    .put("id", "native-image-demo")
                    .put("originalId", mediaId)
                    .put("editedId", mediaId)
                    .put("createdAt", System.currentTimeMillis())) else JSONArray()))
        }
        val notes = JSONArray().put(JSONObject()
            .put("id", "native-note-demo")
            .put("legacyNativeId", "note:demo")
            .put("date", Instant.now().toString())
            .put("location", "Demo-Anlage")
            .put("system", "Miniaturgolf\n(Eternit)")
            .put("holes", holes))

        onProgress(4, "Demo-Bild wird geprüft …", 52)
        val payload = JSONObject()
            .put("format", "minigolf-native-migration")
            .put("version", 1)
            .put("createdAt", Instant.now().toString())
            .put("sourceDatabaseVersion", 9)
            .put("games", games)
            .put("tournamentNotes", notes)
            .put("media", media)
            .put("settings", JSONObject()
                .put("vib", true)
                .put("sound", true)
                .put("wake", false)
                .put("full", false)
                .put("tournamentEnabled", true)
                .put("stats", true)
                .put("tournamentTheme", "System"))
            .put("expected", JSONObject()
                .put("activeGames", 1)
                .put("endedGames", 2)
                .put("tournamentNotes", 1)
                .put("media", 1)
                .put("missingImages", 0))
            .put("warnings", JSONArray())
        return LegacyPayloadResult(payload, warnings)
    }

    private fun convertGame(cursor: Cursor, isCompleted: Boolean, warnings: MutableList<String>): JSONObject {
        val nativeId = cursor.longOr("id", System.currentTimeMillis())
        val playersSource = parseArray(cursor.stringOr("playersJson", "[]"), warnings, "Spiel $nativeId")
        val players = JSONArray()
        val totals = JSONArray()
        for (index in 0 until playersSource.length()) {
            val source = playersSource.optJSONObject(index) ?: continue
            val rounds = source.optJSONArray("holeScores")
            val roundScores = JSONArray()
            if (rounds != null && rounds.length() > 0) {
                for (roundIndex in 0 until rounds.length()) {
                    val sourceRound = rounds.optJSONArray(roundIndex) ?: JSONArray()
                    val targetRound = JSONArray()
                    for (hole in 0 until 18) {
                        val value = sourceRound.opt(hole)
                        targetRound.put(if (value == null || value == JSONObject.NULL) JSONObject.NULL else (value as? Number)?.toInt() ?: JSONObject.NULL)
                    }
                    roundScores.put(targetRound)
                }
            } else {
                val totalsOnly = source.optJSONArray("rounds")
                repeat((totalsOnly?.length() ?: 1).coerceAtLeast(1)) {
                    roundScores.put(JSONArray(List(18) { JSONObject.NULL }))
                }
                warnings.add("Spiel $nativeId enthält nur Rundensummen; einzelne Bahnschläge konnten nicht rekonstruiert werden.")
            }
            val name = source.optString("name", "Spieler ${index + 1}")
            val colorInt = source.optLong("colorInt", 0xFFB71C2A)
            players.put(JSONObject()
                .put("name", name)
                .put("color", colorHex(colorInt))
                .put("roundScores", roundScores))
            totals.put(JSONObject()
                .put("name", name)
                .put("total", source.optInt("totalScore", sumRoundScores(roundScores))))
        }

        return JSONObject()
            .put("id", "native-game-$nativeId")
            .put("legacyNativeId", "game:$nativeId")
            .put("date", iso(cursor.longOr("date", System.currentTimeMillis())))
            .put("reason", if (isCompleted) "Migriert: beendetes Spiel" else "Migriert: aktives Spiel")
            .put("system", cursor.stringOr("system", "Miniaturgolf\n(Eternit)"))
            .put("location", cursor.stringOr("location", ""))
            .put("hasStats", cursor.intOr("hasStats", 0) != 0)
            .put("isCompleted", isCompleted)
            .put("players", players)
            .put("total", totals)
    }

    private fun addMediaFile(
        path: String,
        target: MutableMap<String, JSONObject>,
        warnings: MutableList<String>,
        accept: (ByteArray) -> Boolean
    ): String? {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            warnings.add("Bild fehlt: $path")
            return null
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            warnings.add("Bild ist größer als 24 MB und wurde übersprungen: ${file.name}")
            return null
        }
        return runCatching {
            val bytes = file.readBytes()
            val digest = sha256(bytes)
            val id = "native-media-${digest.take(24)}"
            if (target.containsKey(id)) return id
            if (!accept(bytes)) {
                warnings.add("Gesamtgröße der Bilder überschreitet 48 MB; ${file.name} wurde übersprungen.")
                return null
            }
            target.putIfAbsent(id, JSONObject()
                .put("id", id)
                .put("mimeType", mimeType(file.name, bytes))
                .put("size", bytes.size)
                .put("sha256", digest)
                .put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            id
        }.getOrElse {
            warnings.add("Bild konnte nicht gelesen werden: ${file.name} (${it.message})")
            null
        }
    }

    private fun addEmbeddedMedia(
        base64: String,
        target: MutableMap<String, JSONObject>,
        warnings: MutableList<String>,
        accept: (ByteArray) -> Boolean
    ): String? {
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.size > MAX_IMAGE_BYTES) {
                warnings.add("Eingebettetes Bild ist größer als 24 MB und wurde übersprungen.")
                return null
            }
            val digest = sha256(bytes)
            val id = "native-media-${digest.take(24)}"
            if (target.containsKey(id)) return id
            if (!accept(bytes)) {
                warnings.add("Gesamtgröße der Bilder überschreitet 48 MB; ein eingebettetes Bild wurde übersprungen.")
                return null
            }
            target.putIfAbsent(id, JSONObject()
                .put("id", id)
                .put("mimeType", mimeType("image", bytes))
                .put("size", bytes.size)
                .put("sha256", digest)
                .put("base64", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            id
        }.getOrElse {
            warnings.add("Eingebettetes Bild konnte nicht gelesen werden: ${it.message}")
            null
        }
    }

    private class MediaBudget(private var remainingBytes: Long) {
        fun reserve(size: Int): Boolean {
            if (size < 0 || size.toLong() > remainingBytes) return false
            remainingBytes -= size.toLong()
            return true
        }
    }

    private fun readLegacySettings(warnings: MutableList<String>): JSONObject {
        val prefs = context.getSharedPreferences("minigolf_prefs", Context.MODE_PRIVATE)
        val theme = when (prefs.getInt("tournament_theme", 2)) {
            0 -> "Hell"
            1 -> "Dunkel"
            else -> "System"
        }
        val customBackground = prefs.getString("custom_background_uri", null)
        if (!customBackground.isNullOrBlank()) {
            warnings.add("Das benutzerdefinierte Hintergrundbild wurde aus Sicherheits- und Speichergründen nicht automatisch übernommen.")
        }
        return JSONObject()
            .put("vib", prefs.getBoolean("haptic_enabled", true))
            .put("sound", prefs.getBoolean("sound_enabled", true))
            .put("wake", prefs.getBoolean("keep_screen_on", false))
            .put("full", prefs.getBoolean("full_screen_enabled", true))
            .put("tournamentEnabled", prefs.getBoolean("turnier_mode", false))
            .put("stats", prefs.getBoolean("stats_active", false))
            .put("tournamentTheme", theme)
    }

    private fun backupDatabaseFiles(databaseFile: File, warnings: MutableList<String>) {
        val backupDirectory = File(context.filesDir, "legacy-room-backup-v1")
        val marker = File(backupDirectory, "backup-complete.txt")
        if (marker.exists()) return
        runCatching {
            backupDirectory.mkdirs()
            listOf(
                databaseFile,
                File(databaseFile.absolutePath + "-wal"),
                File(databaseFile.absolutePath + "-shm")
            ).filter { it.exists() }.forEach { source ->
                source.copyTo(File(backupDirectory, source.name), overwrite = true)
            }
            marker.writeText("Backup erstellt: ${Instant.now()}\nQuelle: ${databaseFile.absolutePath}\n")
        }.onFailure {
            warnings.add("Die zusätzliche interne Datenbanksicherung konnte nicht erstellt werden: ${it.message}")
        }
    }

    private fun createDemoImage(): ByteArray {
        val bitmap = Bitmap.createBitmap(640, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(238, 244, 238))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 96, 55)
            textSize = 42f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("MiniGolf Migration", 320f, 190f, paint)
        paint.textSize = 28f
        paint.isFakeBoldText = false
        canvas.drawText("Demo-Bild erfolgreich übertragen", 320f, 245f, paint)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun openReadOnly(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    private fun countWhere(db: SQLiteDatabase, table: String, where: String?): Int {
        if (tableColumns(db, table).isEmpty()) return 0
        val sql = buildString {
            append("SELECT COUNT(*) FROM ").append(table)
            if (!where.isNullOrBlank()) append(" WHERE ").append(where)
        }
        return runCatching {
            db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }.getOrDefault(0)
    }

    private fun tableColumns(db: SQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        return runCatching {
            db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (nameIndex >= 0) cursor.getString(nameIndex)?.let(columns::add)
                }
            }
            columns
        }.getOrDefault(emptySet())
    }

    private fun parseArray(value: String, warnings: MutableList<String>, label: String): JSONArray =
        runCatching { JSONArray(value) }.getOrElse {
            warnings.add("$label enthält ungültige JSON-Daten und wurde leer übernommen.")
            JSONArray()
        }

    private fun Cursor.index(name: String): Int = getColumnIndex(name)
    private fun Cursor.stringOr(name: String, fallback: String): String {
        val index = index(name)
        return if (index >= 0 && !isNull(index)) getString(index) ?: fallback else fallback
    }
    private fun Cursor.longOr(name: String, fallback: Long): Long {
        val index = index(name)
        return if (index >= 0 && !isNull(index)) getLong(index) else fallback
    }
    private fun Cursor.intOr(name: String, fallback: Int): Int {
        val index = index(name)
        return if (index >= 0 && !isNull(index)) getInt(index) else fallback
    }

    private fun colorHex(value: Long): String = String.format(Locale.US, "#%06X", value and 0xFFFFFF)
    private fun iso(value: Long): String = runCatching { Instant.ofEpochMilli(value).toString() }.getOrDefault(Instant.now().toString())

    private fun sumRoundScores(rounds: JSONArray): Int {
        var sum = 0
        for (roundIndex in 0 until rounds.length()) {
            val round = rounds.optJSONArray(roundIndex) ?: continue
            for (hole in 0 until round.length()) sum += round.optInt(hole, 0)
        }
        return sum
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun mimeType(name: String, bytes: ByteArray): String {
        if (bytes.size >= 12) {
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "image/png"
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return "image/jpeg"
            if (String(bytes.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" && String(bytes.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP") return "image/webp"
        }
        return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
