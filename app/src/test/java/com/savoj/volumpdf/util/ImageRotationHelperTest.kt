package com.savoj.volumpdf.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageRotationHelperTest {
    @Test fun normalize_wrapsAndHandlesNegative() {
        assertEquals(0, ImageRotationHelper.normalize(360))
        assertEquals(270, ImageRotationHelper.normalize(-90))
        assertEquals(90, ImageRotationHelper.normalize(450))
    }

    @Test fun nextClockwise_cyclesThroughFourAngles() {
        assertEquals(90, ImageRotationHelper.nextClockwise(0))
        assertEquals(180, ImageRotationHelper.nextClockwise(90))
        assertEquals(270, ImageRotationHelper.nextClockwise(180))
        assertEquals(0, ImageRotationHelper.nextClockwise(270))
    }

    @Test fun nextClockwise_normalizesOddInput() {
        assertEquals(90, ImageRotationHelper.nextClockwise(360))
        assertEquals(0, ImageRotationHelper.nextClockwise(-90))
    }
}
