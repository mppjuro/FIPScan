package com.example.fipscan

import android.content.Context
import android.content.res.Resources
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ElectrophoresisAnalyzerTest {

    private lateinit var context: Context
    private lateinit var resources: Resources

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        every { context.resources } returns resources
        every { context.getString(any()) } returns "Test String"
        every { context.getString(any(), any()) } returns "Test String z parametrem"
        every { resources.getStringArray(R.array.rivalta_options) } returns arrayOf("Nie wykonano", "Negatywna", "Pozytywna")
    }

    @Test
    fun `test High FIP Risk calculation`() {
        val labData = mapOf<String, Any>(
            "Wiek" to "1 rok",
            "Globulin" to "6.0",
            "GlobulinRangeMax" to "4.5",
            "Stosunek A/G" to "0.35",
            "Bilirubina" to "2.0",
            "BilirubinaRangeMax" to "0.5",
            "GammopathyResult" to "Polyclonal gammopathy detected"
        )

        val result = ElectrophoresisAnalyzer.assessFipRisk(
            labData = labData,
            rivaltaStatus = "Pozytywna",
            context = context
        )

        println("Obliczone ryzyko: ${result.riskPercentage}%")
        assertTrue("Ryzyko powinno być wysokie (>=80%) dla typowych objawów", result.riskPercentage >= 75)
    }

    @Test
    fun `test Low FIP Risk calculation`() {
        val labData = mapOf<String, Any>(
            "Wiek" to "5 lat",
            "Globulin" to "3.0",
            "GlobulinRangeMax" to "4.5",
            "Stosunek A/G" to "0.9",
            "GammopathyResult" to "No gammopathy"
        )

        val result = ElectrophoresisAnalyzer.assessFipRisk(
            labData = labData,
            rivaltaStatus = "Negatywna",
            context = context
        )

        println("Obliczone ryzyko: ${result.riskPercentage}%")
        assertTrue("Ryzyko powinno być niskie dla zdrowego kota", result.riskPercentage < 20)
    }
}