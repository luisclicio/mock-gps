package dev.local.mockgps.location

import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.os.SystemClock
import dev.local.mockgps.geo.CoordinateJitter
import dev.local.mockgps.geo.Coordinates
import dev.local.mockgps.geo.JitterConfig

class MockLocationController(context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
        ?: error("O serviço de localização não está disponível.")
    private val jitter = CoordinateJitter(Coordinates(0.0, 0.0))

    private var registeredByThisController = false
    private var baseCoordinates = Coordinates(0.0, 0.0)
    private var jitterEnabled = false

    fun start(coordinates: Coordinates, useJitter: Boolean) {
        baseCoordinates = coordinates
        jitter.setBase(coordinates)
        jitterEnabled = useJitter

        if (!registeredByThisController) registerProvider()
        locationManager.setTestProviderEnabled(PROVIDER, true)
        publishNext()
    }

    fun updateBase(coordinates: Coordinates) {
        baseCoordinates = coordinates
        jitter.setBase(coordinates)
        publishNext()
    }

    fun setJitterEnabled(enabled: Boolean) {
        if (jitterEnabled == enabled) return
        jitterEnabled = enabled
        jitter.setBase(baseCoordinates)
        publishNext()
    }

    fun publishNext() {
        if (!registeredByThisController) return

        val coordinates = jitter.next(enabled = jitterEnabled)
        val location = Location(PROVIDER).apply {
            latitude = coordinates.latitude
            longitude = coordinates.longitude
            accuracy = JitterConfig.ACCURACY_METERS
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        locationManager.setTestProviderLocation(PROVIDER, location)
    }

    fun stop() {
        if (!registeredByThisController) return

        var cleanupFailure: RuntimeException? = null
        try {
            locationManager.setTestProviderEnabled(PROVIDER, false)
        } catch (error: RuntimeException) {
            cleanupFailure = error
        }

        var providerRemoved = false
        try {
            locationManager.removeTestProvider(PROVIDER)
            providerRemoved = true
        } catch (_: IllegalArgumentException) {
            providerRemoved = true
        } catch (error: RuntimeException) {
            if (cleanupFailure == null) cleanupFailure = error
        }

        if (providerRemoved) {
            registeredByThisController = false
        }

        cleanupFailure?.let { throw it }
    }

    private fun registerProvider() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setHasMonetaryCost(false)
                .setHasAltitudeSupport(false)
                .setHasSpeedSupport(false)
                .setHasBearingSupport(false)
                .build()
            locationManager.addTestProvider(PROVIDER, properties)
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                PROVIDER,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                ProviderProperties.POWER_USAGE_LOW,
                ProviderProperties.ACCURACY_FINE,
            )
        }
        registeredByThisController = true
    }

    companion object {
        const val PROVIDER = LocationManager.GPS_PROVIDER

        fun isMockLocationAuthorized(context: Context): Boolean {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            return appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        }
    }
}
