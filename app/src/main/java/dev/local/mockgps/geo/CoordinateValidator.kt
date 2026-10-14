package dev.local.mockgps.geo

enum class CoordinateField {
    LATITUDE,
    LONGITUDE,
}

enum class CoordinateError {
    REQUIRED,
    NOT_A_NUMBER,
    OUT_OF_RANGE,
}

sealed interface CoordinateValidation {
    data class Valid(val coordinates: Coordinates) : CoordinateValidation

    data class Invalid(
        val field: CoordinateField,
        val error: CoordinateError,
    ) : CoordinateValidation
}

object CoordinateValidator {
    fun validate(latitudeText: String, longitudeText: String): CoordinateValidation {
        val latitude = parse(latitudeText, -90.0, 90.0)
        if (latitude is Parsed.Invalid) {
            return CoordinateValidation.Invalid(CoordinateField.LATITUDE, latitude.error)
        }

        val longitude = parse(longitudeText, -180.0, 180.0)
        if (longitude is Parsed.Invalid) {
            return CoordinateValidation.Invalid(CoordinateField.LONGITUDE, longitude.error)
        }

        latitude as Parsed.Value
        longitude as Parsed.Value
        return CoordinateValidation.Valid(Coordinates(latitude.value, longitude.value))
    }

    fun isValid(coordinates: Coordinates): Boolean =
        coordinates.latitude.isFinite() && coordinates.latitude in -90.0..90.0 &&
            coordinates.longitude.isFinite() && coordinates.longitude in -180.0..180.0

    private fun parse(
        rawValue: String,
        minimum: Double,
        maximum: Double,
    ): Parsed {
        val value = rawValue.trim()
        if (value.isEmpty()) return Parsed.Invalid(CoordinateError.REQUIRED)

        val number = value.replace(',', '.').toDoubleOrNull()
            ?: return Parsed.Invalid(CoordinateError.NOT_A_NUMBER)
        if (!number.isFinite()) return Parsed.Invalid(CoordinateError.NOT_A_NUMBER)
        if (number !in minimum..maximum) {
            return Parsed.Invalid(CoordinateError.OUT_OF_RANGE)
        }
        return Parsed.Value(number)
    }

    private sealed interface Parsed {
        data class Value(val value: Double) : Parsed
        data class Invalid(val error: CoordinateError) : Parsed
    }
}
