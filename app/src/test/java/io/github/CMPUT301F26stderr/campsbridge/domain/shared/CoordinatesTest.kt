package io.github.CMPUT301F26stderr.campsbridge.domain.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Owns geographic value boundaries before coordinates reach any SDK or map. */
class CoordinatesTest {
    /** Geographic endpoints remain valid coordinates. */
    @Test fun acceptsGeographicEndpoints() {
        assertEquals(-90.0, Coordinates(-90.0, -180.0).latitude, 0.0)
        assertEquals(180.0, Coordinates(90.0, 180.0).longitude, 0.0)
    }

    /** Out-of-range and nonfinite components cannot enter the application model. */
    @Test fun rejectsInvalidComponents() {
        for (latitude in listOf(-90.0001, 90.0001, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Coordinates(latitude, 0.0) }
        }
        for (longitude in listOf(-180.0001, 180.0001, Double.NaN, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { Coordinates(0.0, longitude) }
        }
    }
}
