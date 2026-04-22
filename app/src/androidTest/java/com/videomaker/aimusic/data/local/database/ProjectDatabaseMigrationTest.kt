package com.videomaker.aimusic.data.local.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectDatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ProjectDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate11To12_addsHookStartTimeDefault_andValidatesSchema() {
        migrationHelper.createDatabase(TEST_DB, 11).apply {
            execSQL(
                """
                INSERT INTO liked_songs (
                    songId,
                    name,
                    artist,
                    coverUrl,
                    mp3Url,
                    previewUrl,
                    durationMs,
                    likedAt
                ) VALUES (
                    77,
                    'Legacy Hook',
                    'Migration Artist',
                    'https://example.com/cover.jpg',
                    'https://example.com/song.mp3',
                    'https://example.com/preview.mp3',
                    123000,
                    456789
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            TEST_DB,
            12,
            true,
            ProjectDatabase.MIGRATION_11_12
        )

        migratedDb.query(
            "SELECT hookStartTimeMs FROM liked_songs WHERE songId = 77"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("hookStartTimeMs")))
        }
    }

    companion object {
        private const val TEST_DB = "project-database-migration-test"
    }
}
