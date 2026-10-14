package dev.local.mockgps.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object JitterConfig {
    const val MAX_RADIUS_METERS = 5.0
    const val UPDATE_INTERVAL_MILLIS = 1_000L
    const val ACCURACY_METERS = 5f

    const val SMOOTHING_FACTOR = 0.78
    const val STEP_METERS = 0.9
    const val EARTH_RADIUS_METERS = 6_371_000.0
}

class CoordinateJitter(
    base: Coordinates,
    private val seed: Int? = null,
) {
    private var base = base
    private var random = createRandom()
    private var eastMeters = 0.0
    private var northMeters = 0.0

    init {
        require(CoordinateValidator.isValid(base)) { "A posição base precisa ser válida." }
    }

    fun setBase(newBase: Coordinates) {
        require(CoordinateValidator.isValid(newBase)) { "A posição base precisa ser válida." }
        base = newBase
        eastMeters = 0.0
        northMeters = 0.0
        random = createRandom()
    }

    fun next(enabled: Boolean): Coordinates {
        if (!enabled) return base

        eastMeters = eastMeters * JitterConfig.SMOOTHING_FACTOR +
            random.nextDouble(-JitterConfig.STEP_METERS, JitterConfig.STEP_METERS)
        northMeters = northMeters * JitterConfig.SMOOTHING_FACTOR +
            random.nextDouble(-JitterConfig.STEP_METERS, JitterConfig.STEP_METERS)
        boundOffset()

        return offsetCoordinate(base, eastMeters, northMeters)
    }

    private fun boundOffset() {
        val distance = hypot(eastMeters, northMeters)
        if (distance > JitterConfig.MAX_RADIUS_METERS) {
            val scale = JitterConfig.MAX_RADIUS_METERS / distance
            eastMeters *= scale
            northMeters *= scale
        }
    }

    private fun createRandom(): Random = Random(seed ?: Random.Default.nextInt())

    private fun offsetCoordinate(
        origin: Coordinates,
        east: Double,
        north: Double,
    ): Coordinates {
        val distance = hypot(east, north)
        if (distance == 0.0) return origin

        val latitude1 = Math.toRadians(origin.latitude)
        val longitude1 = Math.toRadians(origin.longitude)
        val bearing = atan2(east, north)
        val angularDistance = distance / JitterConfig.EARTH_RADIUS_METERS

        val sinLatitude2 = sin(latitude1) * cos(angularDistance) +
            cos(latitude1) * sin(angularDistance) * cos(bearing)
        val latitude2 = asin(sinLatitude2.coerceIn(-1.0, 1.0))
        val longitude2 = longitude1 + atan2(
            sin(bearing) * sin(angularDistance) * cos(latitude1),
            cos(angularDistance) - sin(latitude1) * sin(latitude2),
        )

        return Coordinates(
            latitude = Math.toDegrees(latitude2).coerceIn(-90.0, 90.0),
            longitude = normalizeLongitude(Math.toDegrees(longitude2)),
        )
    }

    private fun normalizeLongitude(longitude: Double): Double =
        ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}
