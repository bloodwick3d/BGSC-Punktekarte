# Gson Verschleierungsschutz
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.annotations.SerializedName { *; }

# Deine Datenmodelle schützen (WICHTIG für Room & Gson)
-keep class de.bgsc.minigolf.GameResult { *; }
-keep class de.bgsc.minigolf.PlayerScore { *; }
-keep class de.bgsc.minigolf.TournamentNoteResult { *; }
-keep class de.bgsc.minigolf.HoleNote { *; }
-keep class de.bgsc.minigolf.GitHubRelease { *; }
-keep class de.bgsc.minigolf.GitHubAsset { *; }

# OkHttp Verschleierungsschutz
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room Verschleierungsschutz
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep models used by @Keep annotation
-keep @androidx.annotation.Keep class * {*;}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}
