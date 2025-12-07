package com.example.fipscan

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractDataTest {

    @Test
    fun `test parsing lab results with standard format`() {
        val inputLines = listOf(
            "Badanie Wynik; Jedn.; Norma",
            "Albuminy 2,8; g/dl; 2,7-3,9",
            "Globuliny 4,5; g/dl; 2,6-5,1",
            "Stosunek A/G 0,62"
        )

        val result = ExtractData.parseLabResults(inputLines)

        assertEquals("2.8", result["Albuminy"])
        assertEquals("4.5", result["Globuliny"])
        assertEquals("0.62", result["Stosunek A/G"])

        assertEquals("2.7", result["AlbuminyRangeMin"])
        assertEquals("3.9", result["AlbuminyRangeMax"])
    }

    @Test
    fun `test parsing date extraction`() {
        val lines = listOf("Data pobrania materiału: 15.03.2024")
        val result = ExtractData.parseLabResults(lines)
        assertEquals("15.03.2024", result["Data"])
    }
}