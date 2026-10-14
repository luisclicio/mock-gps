package dev.local.mockgps.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateJitterTest {
    @Test
    fun disabledVariationReturnsTheExactBaseCoordinate() {
        val base = Coordinates(-3.7319, -38.5267)
        val jitter = CoordinateJitter(base, seed = 1234)

        repeat(10) {
            assertEquals(base, jitter.next(enabled = false))
        }
    }

    @Test
    fun seededInstancesProduceTheSameSmoothSequence() {
        val base = Coordinates(-3.7319, -38.5267)
        val first = CoordinateJitter(base, seed = 1234)
        val second = CoordinateJitter(base, seed = 1234)

        repeat(100) {
            assertEquals(first.next(enabled = true), second.next(enabled = true))
        }
    }

    @Test
    fun movementStaysWithinFiveMetersWithoutDrift() {
        val base = Coordinates(37.4219999, -122.0840575)
        val jitter = CoordinateJitter(base, seed = 7)

        repeat(10_000) {
            val point = jitter.next(enabled = true)
            assertTrue(CoordinateValidator.isValid(point))
            assertTrue(distanceMeters(base, point) <= JitterConfig.MAX_RADIUS_METERS + 0.01)
        }
    }

    @Test
    fun movementRemainsValidAtPolesAndLongitudeBoundaries() {
        val bases = listOf(
            Coordinates(90.0, 179.99999),
            Coordinates(-90.0, -179.99999),
            Coordinates(89.999999, 0.0),
            Coordinates(0.0, 179.99999),
            Coordinates(0.0, -179.99999),
        )

        bases.forEachIndexed { index, base ->
            val jitter = CoordinateJitter(base, seed = index)
            repeat(100) {
                val point = jitter.next(enabled = true)
                assertTrue(CoordinateValidator.isValid(point))
                assertTrue(distanceMeters(base, point) <= JitterConfig.MAX_RADIUS_METERS + 0.01)
            }
        }
    }

    @Test
    fun changingBaseResetsMovementState() {
        val newBase = Coordinates(0.0, 180.0)
        val changed = CoordinateJitter(Coordinates(20.0, 20.0), seed = 55)
        val fresh = CoordinateJitter(newBase, seed = 55)

        repeat(50) { changed.next(enabled = true) }
        changed.setBase(newBase)

        assertEquals(fresh.next(enabled = true), changed.next(enabled = true))
    }

    private fun distanceMeters(first: Coordinates, second: Coordinates): Double {
        val earthRadius = 6_371_000.0
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val latitudeDelta = secondLatitude - firstLatitude
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val haversine = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
            kotlin.math.cos(firstLatitude) * kotlin.math.cos(secondLatitude) *
            kotlin.math.sin(longitudeDelta / 2).let { it * it }
        return 2 * earthRadius * kotlin.math.asin(kotlin.math.sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}
