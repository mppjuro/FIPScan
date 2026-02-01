package com.example.fipscan

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FipPatternAnalyzerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.getString(any()) } returns ""
    }

    @Test
    fun `test detection of Classic Effusive FIP`() {
        val labData = mapOf<String, Any>(
            "Globulin" to "6.0", "GlobulinRangeMax" to "4.0",
            "Albumin" to "1.5", "AlbuminRangeMin" to "2.5",
            "LYM" to "0.5", "LYMRangeMin" to "1.0",
            "Bilirubina" to "2.0", "BilirubinaRangeMax" to "0.5"
        )

        val result = FipPatternAnalyzer.analyzeParameterPatterns(labData, context)

        assertEquals(
            "Powinien wykryć postać wysiękową (klasyczną)",
            FipPatternAnalyzer.FipProfile.EFFUSIVE_CLASSIC,
            result.primaryProfile
        )
    }

    @Test
    fun `test detection of Non-FIP`() {
        val labData = mapOf<String, Any>(
            "Globulin" to "3.0", "GlobulinRangeMax" to "4.0",
            "Albumin" to "3.0", "AlbuminRangeMin" to "2.5"
        )

        val result = FipPatternAnalyzer.analyzeParameterPatterns(labData, context)

        assertEquals(
            "Powinien zaklasyfikować jako Non-FIP",
            FipPatternAnalyzer.FipProfile.NON_FIP,
            result.primaryProfile
        )
    }
}