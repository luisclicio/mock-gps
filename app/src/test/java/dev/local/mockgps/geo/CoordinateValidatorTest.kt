package dev.local.mockgps.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateValidatorTest {
    @Test
    fun acceptsLimitsAndDecimalSeparators() {
        assertEquals(
            CoordinateValidation.Valid(Coordinates(-90.0, 180.0)),
            CoordinateValidator.validate("-90", "180"),
        )
        assertEquals(
            CoordinateValidation.Valid(Coordinates(-3.75, -38.52)),
            CoordinateValidator.validate("-3,75", "-38.52"),
        )
    }

    @Test
    fun reportsEmptyLatitudeAndLongitude() {
        assertEquals(
            CoordinateValidation.Invalid(CoordinateField.LATITUDE, CoordinateError.REQUIRED),
            CoordinateValidator.validate("", "-38.5"),
        )
        assertEquals(
            CoordinateValidation.Invalid(CoordinateField.LONGITUDE, CoordinateError.REQUIRED),
            CoordinateValidator.validate("-3.5", "  "),
        )
    }

    @Test
    fun reportsMalformedAndOutOfRangeValues() {
        assertEquals(
            CoordinateValidation.Invalid(CoordinateField.LATITUDE, CoordinateError.NOT_A_NUMBER),
            CoordinateValidator.validate("norte", "0"),
        )
        assertEquals(
            CoordinateValidation.Invalid(CoordinateField.LATITUDE, CoordinateError.OUT_OF_RANGE),
            CoordinateValidator.validate("90.01", "0"),
        )
        assertEquals(
            CoordinateValidation.Invalid(CoordinateField.LONGITUDE, CoordinateError.OUT_OF_RANGE),
            CoordinateValidator.validate("0", "-180.01"),
        )
    }

    @Test
    fun rejectsNonFiniteAndMixedSeparators() {
        assertEquals(
            CoordinateValidation.Invalid(CoordinateField.LATITUDE, CoordinateError.NOT_A_NUMBER),
            CoordinateValidator.validate("NaN", "0"),
        )
        assertEquals(
            CoordinateValidation.Invalid(CoordinateField.LATITUDE, CoordinateError.NOT_A_NUMBER),
            CoordinateValidator.validate("Infinity", "0"),
        )
        assertTrue(CoordinateValidator.validate("1,234.5", "0") is CoordinateValidation.Invalid)
    }
}
