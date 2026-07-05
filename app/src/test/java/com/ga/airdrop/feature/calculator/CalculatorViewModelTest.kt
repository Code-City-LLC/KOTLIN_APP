package com.ga.airdrop.feature.calculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CalculatorViewModelTest {

    private class NoopCalculatorRepository : CalculatorRepository {
        override suspend fun calculateShipment(
            shippingMethod: String,
            invoiceAmount: Double,
            weightLbs: Double?,
            numberOfPackages: Int,
            lengthInches: Double?,
            widthInches: Double?,
            heightInches: Double?,
        ): ShipmentCalculation = error("Standard calculator tests do not call the remote API")

        override suspend fun searchProducts(query: String, limit: Int): List<CalcProduct> = emptyList()

        override suspend fun usdToJmdRate(): Double = RemoteCalculatorRepository.USD_TO_JMD_FALLBACK
    }

    @Test
    fun standardCalculationUsesEnteredWeightWithoutMultiplyingPackages() {
        val viewModel = CalculatorViewModel(NoopCalculatorRepository())

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("100")
        viewModel.onActualWeightChange("2")
        viewModel.calculate()

        val result = viewModel.result.value
        assertNotNull(result)
        assertEquals(2.0, result!!.weightLbs, 0.001)
        assertEquals(9.0, resolveCharges(result).freight, 0.001)
    }

    @Test
    fun standardCalculationFallsBackToPackageCountWhenWeightBlank() {
        val viewModel = CalculatorViewModel(NoopCalculatorRepository())

        viewModel.onPackagesChange("3")
        viewModel.onInvoiceChange("100")
        viewModel.calculate()

        val result = viewModel.result.value
        assertNotNull(result)
        assertEquals(3.0, result!!.weightLbs, 0.001)
        assertEquals(12.0, resolveCharges(result).freight, 0.001)
    }
}
