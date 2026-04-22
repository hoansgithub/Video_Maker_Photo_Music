package com.videomaker.aimusic.data.local.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class LegacyDurationMigrationTest {

    @Test
    fun migrate10To11_mapsLegacyImageDurationToTotalDuration_andPreservesOtherFields() {
        val database = FakeSupportSQLiteDatabase(
            projects = mutableListOf(
                mutableMapOf<String, Any?>(
                    "id" to "project-1",
                    "name" to "Legacy Project",
                    "createdAt" to 111L,
                    "updatedAt" to 222L,
                    "thumbnailUri" to "content://thumb/1",
                    "imageDurationMs" to 3_500L,
                    "transitionPercentage" to 40,
                    "effectSetId" to "dreamy_vibes",
                    "overlayFrameId" to "frame_gold",
                    "musicSongId" to 77L,
                    "musicSongName" to "Song",
                    "musicSongUrl" to "https://example.com/song.mp3",
                    "musicSongCoverUrl" to "https://example.com/song.jpg",
                    "customAudioUri" to "content://audio/custom",
                    "processedAudioUri" to "content://audio/processed",
                    "audioVolume" to 0.65f,
                    "musicTrimStartMs" to 500L,
                    "musicTrimEndMs" to 6_500L,
                    "aspectRatio" to "RATIO_1_1",
                    "isWatermarkFree" to 1
                ),
                mutableMapOf<String, Any?>(
                    "id" to "project-2",
                    "name" to "No Assets",
                    "createdAt" to 333L,
                    "updatedAt" to 444L,
                    "thumbnailUri" to null,
                    "imageDurationMs" to 2_000L,
                    "transitionPercentage" to 30,
                    "effectSetId" to null,
                    "overlayFrameId" to null,
                    "musicSongId" to null,
                    "musicSongName" to null,
                    "musicSongUrl" to null,
                    "musicSongCoverUrl" to null,
                    "customAudioUri" to null,
                    "processedAudioUri" to null,
                    "audioVolume" to 1.0f,
                    "musicTrimStartMs" to 0L,
                    "musicTrimEndMs" to null,
                    "aspectRatio" to "RATIO_9_16",
                    "isWatermarkFree" to 0
                )
            ),
            assetCountsByProjectId = mapOf(
                "project-1" to 3,
                "project-2" to 0
            )
        )

        ProjectDatabase.MIGRATION_10_11.migrate(database.asRoomDatabase())

        val migratedProject = database.requireProject("project-1")
        assertEquals(10_500L, migratedProject["totalDurationMs"])
        assertEquals("Legacy Project", migratedProject["name"])
        assertEquals(111L, migratedProject["createdAt"])
        assertEquals(222L, migratedProject["updatedAt"])
        assertEquals("content://thumb/1", migratedProject["thumbnailUri"])
        assertEquals(3_500L, migratedProject["imageDurationMs"])
        assertEquals(40, migratedProject["transitionPercentage"])
        assertEquals("dreamy_vibes", migratedProject["effectSetId"])
        assertEquals("frame_gold", migratedProject["overlayFrameId"])
        assertEquals(77L, migratedProject["musicSongId"])
        assertEquals("Song", migratedProject["musicSongName"])
        assertEquals("https://example.com/song.mp3", migratedProject["musicSongUrl"])
        assertEquals("https://example.com/song.jpg", migratedProject["musicSongCoverUrl"])
        assertEquals("content://audio/custom", migratedProject["customAudioUri"])
        assertEquals("content://audio/processed", migratedProject["processedAudioUri"])
        assertEquals(0.65f, migratedProject["audioVolume"])
        assertEquals(500L, migratedProject["musicTrimStartMs"])
        assertEquals(6_500L, migratedProject["musicTrimEndMs"])
        assertEquals("RATIO_1_1", migratedProject["aspectRatio"])
        assertEquals(1, migratedProject["isWatermarkFree"])

        val emptyProject = database.requireProject("project-2")
        assertEquals(0L, emptyProject["totalDurationMs"])
        assertNull(emptyProject["thumbnailUri"])
        assertNull(emptyProject["musicTrimEndMs"])
    }

    @Test
    fun migrate11To12_addsHookStartTimeToLikedSongs_withDefaultValueAndPreservesOtherFields() {
        val database = FakeSupportSQLiteDatabase(
            projects = mutableListOf(),
            assetCountsByProjectId = emptyMap(),
            likedSongs = mutableListOf(
                mutableMapOf<String, Any?>(
                    "songId" to 77L,
                    "name" to "Legacy Hook",
                    "artist" to "Migration Artist",
                    "coverUrl" to "https://example.com/cover.jpg",
                    "mp3Url" to "https://example.com/song.mp3",
                    "previewUrl" to "https://example.com/preview.mp3",
                    "durationMs" to 123_000,
                    "likedAt" to 456_789L
                )
            )
        )

        ProjectDatabase.MIGRATION_11_12.migrate(database.asRoomDatabase())

        val migratedSong = database.requireLikedSong(77L)
        assertEquals(0L, migratedSong["hookStartTimeMs"])
        assertEquals("Legacy Hook", migratedSong["name"])
        assertEquals("Migration Artist", migratedSong["artist"])
        assertEquals("https://example.com/cover.jpg", migratedSong["coverUrl"])
        assertEquals("https://example.com/song.mp3", migratedSong["mp3Url"])
        assertEquals("https://example.com/preview.mp3", migratedSong["previewUrl"])
        assertEquals(123_000, migratedSong["durationMs"])
        assertEquals(456_789L, migratedSong["likedAt"])
    }

    private class FakeSupportSQLiteDatabase(
        private val projects: MutableList<MutableMap<String, Any?>>,
        private val assetCountsByProjectId: Map<String, Int>,
        private val likedSongs: MutableList<MutableMap<String, Any?>> = mutableListOf()
    ) {
        fun asRoomDatabase(): SupportSQLiteDatabase {
            return Proxy.newProxyInstance(
                SupportSQLiteDatabase::class.java.classLoader,
                arrayOf(SupportSQLiteDatabase::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "execSQL" -> {
                        applySql(args?.firstOrNull() as String)
                        Unit
                    }
                    "close" -> Unit
                    "isOpen" -> true
                    "toString" -> "FakeSupportSQLiteDatabase"
                    else -> error("Unexpected call to ${method.name}")
                }
            } as SupportSQLiteDatabase
        }

        fun requireProject(projectId: String): Map<String, Any?> {
            return projects.firstOrNull { it["id"] == projectId }
                ?: error("Missing project $projectId")
        }

        fun requireLikedSong(songId: Long): Map<String, Any?> {
            return likedSongs.firstOrNull { it["songId"] == songId }
                ?: error("Missing liked song $songId")
        }

        private fun applySql(sql: String) {
            val normalizedSql = sql.replace(Regex("\\s+"), " ").trim()
            when {
                normalizedSql.startsWith("ALTER TABLE projects ADD COLUMN totalDurationMs") -> {
                    projects.forEach { it["totalDurationMs"] = 0L }
                }
                normalizedSql.startsWith("UPDATE projects SET totalDurationMs = imageDurationMs * (") -> {
                    projects.forEach { project ->
                        val projectId = project["id"] as String
                        val imageDurationMs = project["imageDurationMs"] as Long
                        val imageCount = assetCountsByProjectId[projectId] ?: 0
                        project["totalDurationMs"] = imageDurationMs * imageCount
                    }
                }
                normalizedSql.startsWith("ALTER TABLE liked_songs ADD COLUMN hookStartTimeMs") -> {
                    likedSongs.forEach { it["hookStartTimeMs"] = 0L }
                }
                else -> error("Unexpected SQL: $normalizedSql")
            }
        }
    }
}
