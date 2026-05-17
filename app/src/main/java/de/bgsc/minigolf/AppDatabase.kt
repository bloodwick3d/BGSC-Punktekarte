package de.bgsc.minigolf

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GameResult::class, TournamentNoteResult::class], 
    version = 9, 
    exportSchema = true
)
@TypeConverters(Converters::class, TournamentConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameResultDao(): GameResultDao
    abstract fun tournamentNoteDao(): TournamentNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration von Version 8 zu 9: Spalte 'hasStats' hinzufügen
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // In SQLite gibt es kein Boolean, daher nutzen wir INTEGER (0 oder 1)
                db.execSQL("ALTER TABLE game_results ADD COLUMN hasStats INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minigolf_database"
                )
                .addMigrations(MIGRATION_8_9)
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
