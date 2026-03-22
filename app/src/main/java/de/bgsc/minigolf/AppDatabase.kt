package de.bgsc.minigolf

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [GameResult::class, TournamentNoteResult::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class, TournamentConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameResultDao(): GameResultDao
    abstract fun tournamentNoteDao(): TournamentNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "minigolf_database"
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
