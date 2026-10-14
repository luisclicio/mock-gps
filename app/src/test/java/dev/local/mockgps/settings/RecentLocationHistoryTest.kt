package dev.local.mockgps.settings

import dev.local.mockgps.geo.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentLocationHistoryTest {
    @Test
    fun keepsTheFiveMostRecentDistinctLocations() {
        val history = (0..6).fold(emptyList<RecentLocation>()) { current, value ->
            RecentLocationHistory.record(
                current,
                Coordinates(latitude = value.toDouble(), longitude = -value.toDouble()),
            )
        }

        assertEquals(
            (6 downTo 2).map { value ->
                RecentLocation(Coordinates(value.toDouble(), -value.toDouble()))
            },
            history,
        )
    }

    @Test
    fun recordingAnExistingLocationMovesItToTheFrontAndPreservesItsLabel() {
        val home = Coordinates(-3.73, -38.52)
        val office = Coordinates(-3.74, -38.50)
        val history = listOf(
            RecentLocation(office),
            RecentLocation(home, "Casa"),
        )

        assertEquals(
            listOf(RecentLocation(home, "Casa"), RecentLocation(office)),
            RecentLocationHistory.record(history, home),
        )
    }

    @Test
    fun updatesLabelsWithoutChangingHistoryOrder() {
        val home = Coordinates(-3.73, -38.52)
        val office = Coordinates(-3.74, -38.50)
        val history = listOf(RecentLocation(home), RecentLocation(office))

        assertEquals(
            listOf(RecentLocation(home, "Casa"), RecentLocation(office)),
            RecentLocationHistory.updateLabel(history, home, "  Casa  "),
        )

        val longLabel = "a".repeat(RecentLocationHistory.MAX_LABEL_LENGTH + 10)
        assertEquals(
            RecentLocationHistory.MAX_LABEL_LENGTH,
            RecentLocationHistory.updateLabel(history, home, longLabel).first().label.length,
        )
    }

    @Test
    fun encodedHistoryRoundTripsLabelsWithUnicodeAndSeparators() {
        val location = RecentLocation(
            Coordinates(-3.73, -38.52),
            "Casa | térreo\nentrada",
        )

        assertEquals(listOf(location), RecentLocationHistory.decode(RecentLocationHistory.encode(listOf(location))))
    }

    @Test
    fun ignoresMalformedOrInvalidStoredLocations() {
        val raw = """
            nope	-38.52	Casa
            91.0	-38.52	Fora do mapa
            -3.73	-38.52	Casa
        """.trimIndent()

        assertEquals(
            listOf(RecentLocation(Coordinates(-3.73, -38.52), "Casa")),
            RecentLocationHistory.decode(raw),
        )
        assertTrue(RecentLocationHistory.decode("").isEmpty())
    }
}
