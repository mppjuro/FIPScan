package com.example.fipscan.math

import kotlin.math.exp
import kotlin.math.pow

data class GaussianComponent(
    val amplitude: Double,
    val mean: Double,
    val sigma: Double
) {
    fun getValueAt(x: Double): Double {
        val numerator = (x - mean).pow(2)
        val denominator = 2 * sigma.pow(2)
        return amplitude * exp(-numerator / denominator)
    }

    fun getFirstDerivativeAt(x: Double): Double {
        val y = getValueAt(x)
        return -y * (x - mean) / sigma.pow(2)
    }

    fun getSecondDerivativeAt(x: Double): Double {
        val y = getValueAt(x)
        val variance = sigma.pow(2)
        val term1 = (x - mean).pow(2) / variance.pow(2)
        val term2 = 1.0 / variance
        return y * (term1 - term2)
    }
}