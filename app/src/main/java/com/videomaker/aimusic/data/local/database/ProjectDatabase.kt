package com.videomaker.aimusic.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.videomaker.aimusic.data.local.database.dao.AssetDao
import com.videomaker.aimusic.data.local.database.dao.ProjectDao
import com.videomaker.aimusic.data.local.database.entity.AssetEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectEntity

/**
 * ProjectDatabase - Room database for storing projects and assets
 *
 * Uses KSP2 for code generation (configured in build.gradle.kts)
 */
@Database(
    entities = [ProjectEntity::class, AssetEntity::class],
    version = 5, // Bumped for audioTrackId -> musicSongId + musicSongUrl (Supabase songs)
    exportSchema = true
)
abstract class ProjectDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun assetDao(): AssetDao

    companion object {
        private const val DATABASE_NAME = "video_maker_database"

        @Volatile
        private var INSTANCE: ProjectDatabase? = null

        fun getInstance(context: Context): ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ProjectDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ProjectDatabase::class.java,
                DATABASE_NAME
            )
                // IMPORTANT: Add proper migrations before production release.
                // fallbackToDestructiveMigration without dropAllTables only drops
                // tables with schema changes, preserving unaffected user data.
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
    }
}
