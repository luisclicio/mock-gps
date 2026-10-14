package dev.local.mockgps.settings

import android.content.Context
import dev.local.mockgps.geo.Coordinates
import dev.local.mockgps.geo.CoordinateValidator

class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun saveCoordinates(coordinates: Coordinates) {
        preferences.edit()
            .putString(KEY_LATITUDE, coordinates.latitude.toString())
            .putString(KEY_LONGITUDE, coordinates.longitude.toString())
            .apply()
    }

    fun loadCoordinates(): Coordinates? {
        val latitude = preferences.getString(KEY_LATITUDE, null)?.toDoubleOrNull() ?: return null
        val longitude = preferences.getString(KEY_LONGITUDE, null)?.toDoubleOrNull() ?: return null
        return Coordinates(latitude, longitude).takeIf(CoordinateValidator::isValid)
    }

    fun saveJitterEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_JITTER_ENABLED, enabled).apply()
    }

    fun isJitterEnabled(): Boolean = preferences.getBoolean(KEY_JITTER_ENABLED, false)

    fun setSimulationActive(active: Boolean) {
        preferences.edit().putBoolean(KEY_SIMULATION_ACTIVE, active).apply()
    }

    fun isSimulationActive(): Boolean = preferences.getBoolean(KEY_SIMULATION_ACTIVE, false)

    fun saveActiveSimulation(coordinates: Coordinates, jitterEnabled: Boolean) {
        preferences.edit()
            .putString(KEY_ACTIVE_LATITUDE, coordinates.latitude.toString())
            .putString(KEY_ACTIVE_LONGITUDE, coordinates.longitude.toString())
            .putBoolean(KEY_ACTIVE_JITTER, jitterEnabled)
            .putBoolean(KEY_SIMULATION_ACTIVE, true)
            .apply()
    }

    fun loadActiveCoordinates(): Coordinates? {
        val latitude = preferences.getString(KEY_ACTIVE_LATITUDE, null)?.toDoubleOrNull() ?: return null
        val longitude = preferences.getString(KEY_ACTIVE_LONGITUDE, null)?.toDoubleOrNull() ?: return null
        return Coordinates(latitude, longitude).takeIf(CoordinateValidator::isValid)
    }

    fun isActiveJitterEnabled(): Boolean = preferences.getBoolean(KEY_ACTIVE_JITTER, false)

    fun saveError(message: String?) {
        preferences.edit().putString(KEY_LAST_ERROR, message).apply()
    }

    fun loadError(): String? = preferences.getString(KEY_LAST_ERROR, null)

    fun loadRecentLocations(): List<RecentLocation> =
        RecentLocationHistory.decode(preferences.getString(KEY_RECENT_LOCATIONS, "").orEmpty())

    fun recordRecentLocation(coordinates: Coordinates): List<RecentLocation> =
        updateRecentLocations { locations -> RecentLocationHistory.record(locations, coordinates) }

    fun saveRecentLocationLabel(coordinates: Coordinates, label: String): List<RecentLocation> =
        updateRecentLocations { locations ->
            RecentLocationHistory.updateLabel(locations, coordinates, label)
        }

    private fun updateRecentLocations(
        update: (List<RecentLocation>) -> List<RecentLocation>,
    ): List<RecentLocation> {
        val locations = update(loadRecentLocations())
        preferences.edit()
            .putString(KEY_RECENT_LOCATIONS, RecentLocationHistory.encode(locations))
            .apply()
        return locations
    }

    private companion object {
        const val PREFERENCES_NAME = "mock_gps_settings"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_JITTER_ENABLED = "jitter_enabled"
        const val KEY_SIMULATION_ACTIVE = "simulation_active"
        const val KEY_ACTIVE_LATITUDE = "active_latitude"
        const val KEY_ACTIVE_LONGITUDE = "active_longitude"
        const val KEY_ACTIVE_JITTER = "active_jitter"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_RECENT_LOCATIONS = "recent_locations"
    }
}
