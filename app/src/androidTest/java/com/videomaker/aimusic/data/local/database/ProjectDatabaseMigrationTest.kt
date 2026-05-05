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

    @Test
    fun migrate13To14_addsTemplateIdColumn_withNullDefault_andValidatesSchema() {
        migrationHelper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                """
                INSERT INTO projects (
                    id,
                    name,
                    createdAt,
                    updatedAt,
                    thumbnailUri,
                    totalDurationMs,
                    effectSetId,
                    overlayFrameId,
                    musicSongId,
                    musicSongName,
                    musicSongUrl,
                    musicSongCoverUrl,
                    customAudioUri,
                    processedAudioUri,
                    audioVolume,
                    aspectRatio,
                    isWatermarkFree
                ) VALUES (
                    'project-13',
                    'Legacy V13 Project',
                    1000,
                    2000,
                    'content://thumb/13',
                    45000,
                    'dreamy_vibes',
                    'frame_gold',
                    77,
                    'Legacy Song',
                    'https://example.com/song.mp3',
                    'https://example.com/cover.jpg',
                    NULL,
                    NULL,
                    0.8,
                    'RATIO_9_16',
                    1
                )
                """.trimIndent()
            )
            close()
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            TEST_DB,
            14,
            true,
            ProjectDatabase.MIGRATION_13_14
        )

        migratedDb.query(
            "SELECT templateId, effectSetId, totalDurationMs FROM projects WHERE id = 'project-13'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())

            val templateIdIndex = cursor.getColumnIndex("templateId")
            assertTrue("templateId column must exist after migration", templateIdIndex >= 0)
            assertTrue(cursor.isNull(templateIdIndex))

            assertEquals("dreamy_vibes", cursor.getString(cursor.getColumnIndexOrThrow("effectSetId")))
            assertEquals(45_000L, cursor.getLong(cursor.getColumnIndexOrThrow("totalDurationMs")))
        }
    }

    companion object {
        private const val TEST_DB = "project-database-migration-test"
    }
}
