package com.videomaker.aimusic.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 12,
    exportSchema = true
)
abstract class ProjectDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun assetDao(): AssetDao
    abstract fun likedSongDao(): LikedSongDao
    abstract fun likedTemplateDao(): LikedTemplateDao

    companion object {
        private const val DATABASE_NAME = "video_maker_database"

        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE projects ADD COLUMN totalDurationMs INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE projects
                    SET totalDurationMs = imageDurationMs * (
                        SELECT COUNT(*)
                        FROM assets
                        WHERE assets.projectId = projects.id
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE liked_songs ADD COLUMN hookStartTimeMs INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

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
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
    }
}
