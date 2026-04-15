package com.videomaker.aimusic.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.videomaker.aimusic.data.local.database.dao.AssetDao
import com.videomaker.aimusic.data.local.database.dao.LikedSongDao
import com.videomaker.aimusic.data.local.database.dao.LikedTemplateDao
import com.videomaker.aimusic.data.local.database.dao.ProjectDao
import com.videomaker.aimusic.data.local.database.entity.AssetEntity
import com.videomaker.aimusic.data.local.database.entity.LikedSongEntity
import com.videomaker.aimusic.data.local.database.entity.LikedTemplateEntity
import com.videomaker.aimusic.data.local.database.entity.ProjectEntity

/**
 * ProjectDatabase - Room database for storing projects, assets, and liked items
 *
 * Uses KSP2 for code generation (configured in build.gradle.kts)
 */
@Database(
    entities = [
        ProjectEntity::class,
        AssetEntity::class,
        LikedSongEntity::class,
        LikedTemplateEntity::class
    ],
    version = 10,  // Notification phase-1 state uses SharedPreferences; no Room schema change required.
    exportSchema = true
)
abstract class ProjectDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun assetDao(): AssetDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun likedTemplateDao(): LikedTemplateDao

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
