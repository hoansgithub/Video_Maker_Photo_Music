package co.alcheclub.video.maker.photo.music.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import co.alcheclub.video.maker.photo.music.data.local.database.dao.AssetDao
import co.alcheclub.video.maker.photo.music.data.local.database.dao.ProjectDao
import co.alcheclub.video.maker.photo.music.data.local.database.entity.AssetEntity
import co.alcheclub.video.maker.photo.music.data.local.database.entity.ProjectEntity

/**
 * ProjectDatabase - Room database for storing projects and assets
 *
 * Uses KSP2 for code generation (configured in build.gradle.kts)
 */
@Database(
    entities = [ProjectEntity::class, AssetEntity::class],
    version = 2, // Bumped for transition settings schema change
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
                // TODO: Replace with proper migration before production release
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
