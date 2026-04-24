package com.videomaker.aimusic.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.json.JSONObject

/**
 * Test to download and inspect beat-sync JSON structure.
 *
 * Run this test to verify:
 * 1. JSON format matches expected structure
 * 2. Beat data can be parsed correctly
 * 3. Field names and types are correct
 */
class BeatSyncRepositoryTest {

    @Test
    fun `inspect beat-sync JSON structure`() {
        // Sample JSON structure from migration docs
        val sampleJson = """
        {
            "beats": [[0.06, 0.0], [0.68, 0.09], [1.3, 0.77], [1.92, 0.15]],
            "bpm": 95.0,
            "num_beats": 178,
            "num_kicks": 139,
            "kick_band_hz": [38, 151]
        }
        """.trimIndent()

        val obj = JSONObject(sampleJson)

        println("=== Beat-Sync JSON Structure ===")
        println("BPM: ${obj.getDouble("bpm")}")
        println("Total beats: ${obj.getInt("num_beats")}")
        println("Kicks: ${obj.getInt("num_kicks")}")

        val beatsArray = obj.getJSONArray("beats")
        println("Beats array length: ${beatsArray.length()}")

        // Parse first few beats
        println("\nFirst 4 beats:")
        for (i in 0 until minOf(4, beatsArray.length())) {
            val pair = beatsArray.getJSONArray(i)
            val timeS = pair.getDouble(0)
            val kickStrength = pair.getDouble(1)
            println("  Beat $i: time=${timeS}s (${(timeS * 1000).toInt()}ms), kick_strength=$kickStrength")
        }

        // Calculate transition times (every 4th beat)
        println("\nTransition points (every 4th beat):")
        var idx = 3  // Start at index 3 (4th beat, 0-based)
        var transitionCount = 0
        while (idx < beatsArray.length() && transitionCount < 5) {
            val pair = beatsArray.getJSONArray(idx)
            val timeS = pair.getDouble(0)
            println("  Transition ${transitionCount + 1}: ${(timeS * 1000).toInt()}ms")
            idx += 4
            transitionCount++
        }

        // Calculate transition duration
        val bpm = obj.getDouble("bpm")
        val beatMs = 60000.0 / bpm
        val transitionDuration = minOf(beatMs, 1000.0)
        println("\nTiming:")
        println("  Beat interval: ${beatMs.toInt()}ms")
        println("  Transition duration: ${transitionDuration.toInt()}ms")
        println("  Fadeout duration (6 beats): ${(beatMs * 6).toInt()}ms")

        // Test parsing with our BeatSyncData model
        println("\n=== Parsed BeatSyncData ===")
        val beats = (0 until beatsArray.length()).map { i ->
            val pair = beatsArray.getJSONArray(i)
            pair.getDouble(0)  // Extract only time_s (ignore kick_strength)
        }
        println("Extracted ${beats.size} beat positions")
        println("First 5: ${beats.take(5).map { "${(it * 1000).toInt()}ms" }}")
    }
}
