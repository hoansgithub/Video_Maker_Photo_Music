package com.videomaker.aimusic.core.rating

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingTriggerManagerTest {

    @Test
    fun `shouldShowRating returns false when completed`() {
        assertFalse(
            shouldShowRating(
                videoCreateCount = 5,
                shownCount = 0,
                completed = true,
                firstShow = 1,
                cap = 3
            )
        )
    }

    @Test
    fun `shouldShowRating returns false when count below firstShow`() {
        assertFalse(
            shouldShowRating(
                videoCreateCount = 0,
                shownCount = 0,
                completed = false,
                firstShow = 1,
                cap = 3
            )
        )
    }

    @Test
    fun `shouldShowRating returns true when count equals firstShow`() {
        assertTrue(
            shouldShowRating(
                videoCreateCount = 1,
                shownCount = 0,
                completed = false,
                firstShow = 1,
                cap = 3
            )
        )
    }

    @Test
    fun `shouldShowRating returns true when count above firstShow and under cap`() {
        assertTrue(
            shouldShowRating(
                videoCreateCount = 4,
                shownCount = 2,
                completed = false,
                firstShow = 1,
                cap = 3
            )
        )
    }

    @Test
    fun `shouldShowRating returns false when shownCount reaches cap`() {
        assertFalse(
            shouldShowRating(
                videoCreateCount = 10,
                shownCount = 3,
                completed = false,
                firstShow = 1,
                cap = 3
            )
        )
    }

    @Test
    fun `shouldShowRating returns false when shownCount exceeds cap`() {
        assertFalse(
            shouldShowRating(
                videoCreateCount = 10,
                shownCount = 5,
                completed = false,
                firstShow = 1,
                cap = 3
            )
        )
    }
}
