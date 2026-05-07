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
    version = 14,
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

        internal val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove legacy columns - beat-sync mode only
                // SQLite doesn't support DROP COLUMN directly, so we recreate the table

                // Create new table with beat-sync schema
                db.execSQL(
                    """
                    CREATE TABLE projects_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        thumbnailUri TEXT,
                        totalDurationMs INTEGER NOT NULL DEFAULT 0,
                        effectSetId TEXT DEFAULT 'dreamy_vibes',
                        overlayFrameId TEXT,
                        musicSongId INTEGER,
                        musicSongName TEXT,
                        musicSongUrl TEXT,
                        musicSongCoverUrl TEXT,
                        customAudioUri TEXT,
                        processedAudioUri TEXT,
                        audioVolume REAL NOT NULL DEFAULT 1.0,
                        aspectRatio TEXT NOT NULL DEFAULT 'RATIO_9_16',
                        isWatermarkFree INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )

                // Copy data from old table (excluding removed columns)
                db.execSQL(
                    """
                    INSERT INTO projects_new (
                        id, name, createdAt, updatedAt, thumbnailUri, totalDurationMs,
                        effectSetId, overlayFrameId, musicSongId, musicSongName,
                        musicSongUrl, musicSongCoverUrl, customAudioUri, processedAudioUri,
                        audioVolume, aspectRatio, isWatermarkFree
                    )
                    SELECT
                        id, name, createdAt, updatedAt, thumbnailUri, totalDurationMs,
                        effectSetId, overlayFrameId, musicSongId, musicSongName,
                        musicSongUrl, musicSongCoverUrl, customAudioUri, processedAudioUri,
                        audioVolume, aspectRatio, isWatermarkFree
                    FROM projects
                    """.trimIndent()
                )

                // Drop old table
                db.execSQL("DROP TABLE projects")

                // Rename new table to original name
                db.execSQL("ALTER TABLE projects_new RENAME TO projects")
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN templateId TEXT")
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
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
    }
}
