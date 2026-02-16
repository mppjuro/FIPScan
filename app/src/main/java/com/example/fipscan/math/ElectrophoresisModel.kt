package com.example.fipscan.math

class ElectrophoresisModel(
    private val components: List<GaussianComponent>,
    private val baseline: Double = 0.0
) {

    fun getY(x: Double): Double {
        return baseline + components.sumOf { it.getValueAt(x) }
    }

    fun getDerivative(x: Double): Double {
        return components.sumOf { it.getFirstDerivativeAt(x) }
    }

    fun getMaxGammaSlope(): Double {
        val gamma = components.lastOrNull() ?: return 0.0
        return gamma.getFirstDerivativeAt(gamma.mean - gamma.sigma)
    }

    fun getBaseline(): Double = baseline
}