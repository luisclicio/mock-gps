package dev.local.mockgps.settings

import dev.local.mockgps.geo.Coordinates
import dev.local.mockgps.geo.CoordinateValidator
import java.net.URLDecoder
import java.net.URLEncoder

data class RecentLocation(
    val coordinates: Coordinates,
    val label: String = "",
)

object RecentLocationHistory {
    const val MAX_LOCATIONS = 5
    const val MAX_LABEL_LENGTH = 40

    fun record(locations: List<RecentLocation>, coordinates: Coordinates): List<RecentLocation> {
        val current = normalize(locations)
        if (!CoordinateValidator.isValid(coordinates)) return current

        val existing = current.firstOrNull { it.coordinates == coordinates }
        return listOf(existing ?: RecentLocation(coordinates)) +
            current.filterNot { it.coordinates == coordinates }.take(MAX_LOCATIONS - 1)
    }

    fun updateLabel(
        locations: List<RecentLocation>,
        coordinates: Coordinates,
        label: String,
    ): List<RecentLocation> = normalize(
        locations.map { location ->
            if (location.coordinates == coordinates) {
                location.copy(label = normalizeLabel(label))
            } else {
                location
            }
        },
    )

    fun remove(locations: List<RecentLocation>, coordinates: Coordinates): List<RecentLocation> =
        normalize(locations.filterNot { it.coordinates == coordinates })

    fun encode(locations: List<RecentLocation>): String = normalize(locations).joinToString("\n") { location ->
        val label = URLEncoder.encode(location.label, Charsets.UTF_8.name())
        "${location.coordinates.latitude}\t${location.coordinates.longitude}\t$label"
    }

    fun decode(encoded: String): List<RecentLocation> {
        val locations = encoded.lineSequence().mapNotNull { line ->
            val fields = line.split('\t', limit = 3)
            if (fields.size != 3) return@mapNotNull null

            val coordinates = Coordinates(
                latitude = fields[0].toDoubleOrNull() ?: return@mapNotNull null,
                longitude = fields[1].toDoubleOrNull() ?: return@mapNotNull null,
            )
            if (!CoordinateValidator.isValid(coordinates)) return@mapNotNull null

            val label = runCatching {
                URLDecoder.decode(fields[2], Charsets.UTF_8.name())
            }.getOrNull() ?: return@mapNotNull null
            RecentLocation(coordinates, label)
        }.toList()

        return normalize(locations)
    }

    private fun normalize(locations: List<RecentLocation>): List<RecentLocation> {
        val unique = LinkedHashMap<Coordinates, RecentLocation>()
        for (location in locations) {
            if (!CoordinateValidator.isValid(location.coordinates)) continue
            unique.putIfAbsent(
                location.coordinates,
                location.copy(label = normalizeLabel(location.label)),
            )
            if (unique.size == MAX_LOCATIONS) break
        }
        return unique.values.toList()
    }

    private fun normalizeLabel(label: String): String = label.trim().take(MAX_LABEL_LENGTH)
}
