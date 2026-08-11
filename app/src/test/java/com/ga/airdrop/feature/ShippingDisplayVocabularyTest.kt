package com.ga.airdrop.feature

import com.ga.airdrop.core.designsystem.theme.TextSizeController
import com.ga.airdrop.data.model.DropAlertShippingMethod as WireDropAlertShippingMethod
import com.ga.airdrop.feature.calculator.ShippingMethod
import com.ga.airdrop.feature.dropalert.DropAlertShippingMethod
import com.ga.airdrop.feature.homedetails.WarehouseType
import com.ga.airdrop.feature.shipments.ShipmentMethodUi
import com.ga.airdrop.feature.shipments.ShipmentTypeFilter
import com.ga.airdrop.feature.shipments.shippingMethodDisplayName
import org.junit.Assert.assertEquals
import org.junit.Test

class ShippingDisplayVocabularyTest {

    @Test
    fun `customer-facing shipping names use the canonical vocabulary`() {
        assertEquals("Airdrop", ShippingMethod.STANDARD.label)
        assertEquals("Express", ShippingMethod.EXPRESS.label)
        assertEquals("Seadrop", ShippingMethod.SEADROP.label)

        assertEquals("Airdrop", WarehouseType.Standard.prettyName)
        assertEquals("Express", WarehouseType.Express.prettyName)
        assertEquals("Seadrop", WarehouseType.SeaDrop.prettyName)

        assertEquals("Airdrop", ShipmentMethodUi.Standard.title)
        assertEquals("Express", ShipmentMethodUi.Express.title)
        assertEquals("Seadrop", ShipmentMethodUi.SeaDrop.title)

        assertEquals("Airdrop", ShipmentTypeFilter.Standard.label)
        assertEquals("Express", ShipmentTypeFilter.Express.label)
        assertEquals("Seadrop", ShipmentTypeFilter.Seadrop.label)

        assertEquals("Airdrop", DropAlertShippingMethod.AIRDROP_STANDARD.displayName)
        assertEquals("Express", DropAlertShippingMethod.EXPRESS.displayName)
        assertEquals("Seadrop", DropAlertShippingMethod.SEADROP_STANDARD.displayName)

        assertEquals(
            DropAlertShippingMethod.AIRDROP_STANDARD,
            DropAlertShippingMethod.fromDisplayNameOrNull("Airdrop Standard"),
        )
        assertEquals(
            DropAlertShippingMethod.SEADROP_STANDARD,
            DropAlertShippingMethod.fromDisplayNameOrNull("SeaDrop Standard"),
        )
        assertEquals(null, DropAlertShippingMethod.fromDisplayNameOrNull("Overseas Freight"))
    }

    @Test
    fun `display rename preserves internal and server contracts`() {
        assertEquals("STANDARD", ShippingMethod.STANDARD.name)
        assertEquals("airdrop_standard", ShippingMethod.STANDARD.apiValue)
        assertEquals("airdrop_express", ShippingMethod.EXPRESS.apiValue)
        assertEquals("seadrop_standard", ShippingMethod.SEADROP.apiValue)

        assertEquals("standard", WarehouseType.Standard.key)
        assertEquals("express", WarehouseType.Express.key)
        assertEquals("seadrop", WarehouseType.SeaDrop.key)

        assertEquals("Standard", ShipmentTypeFilter.Standard.name)
        assertEquals("Express", ShipmentTypeFilter.Express.name)
        assertEquals("Seadrop", ShipmentTypeFilter.Seadrop.name)

        assertEquals("AIR", DropAlertShippingMethod.AIRDROP_STANDARD.apiValue)
        assertEquals("Express", DropAlertShippingMethod.EXPRESS.apiValue)
        assertEquals("SeaDrop", DropAlertShippingMethod.SEADROP_STANDARD.apiValue)

        assertEquals("AIR", WireDropAlertShippingMethod.AIRDROP_STANDARD.wireName)
        assertEquals("Express", WireDropAlertShippingMethod.EXPRESS.wireName)
        assertEquals("SeaDrop", WireDropAlertShippingMethod.SEADROP_STANDARD.wireName)

        assertEquals(ShipmentMethodUi.Standard, ShipmentMethodUi.from("Standard"))
        assertEquals(ShipmentMethodUi.Standard, ShipmentMethodUi.from("AirDrop Standard"))
        assertEquals(ShipmentMethodUi.SeaDrop, ShipmentMethodUi.from("SeaDrop"))
        assertEquals(ShipmentMethodUi.SeaDrop, ShipmentMethodUi.from("seadrop_standard"))
        assertEquals(ShipmentMethodUi.Express, ShipmentMethodUi.from("Express"))

        assertEquals(ShipmentMethodUi.Standard, ShipmentMethodUi.fromOrNull("Airdrop"))
        assertEquals(ShipmentMethodUi.Standard, ShipmentMethodUi.fromOrNull("AIR"))
        assertEquals(ShipmentMethodUi.SeaDrop, ShipmentMethodUi.fromOrNull("Seadrop"))
        assertEquals(ShipmentMethodUi.Express, ShipmentMethodUi.fromOrNull("Express"))
        assertEquals(null, ShipmentMethodUi.fromOrNull(null))
        assertEquals(null, ShipmentMethodUi.fromOrNull(""))
        assertEquals(null, ShipmentMethodUi.fromOrNull("Freight"))
        assertEquals(null, ShipmentMethodUi.fromOrNull("Overseas Freight"))
        assertEquals(null, ShipmentMethodUi.fromOrNull("nonstandard"))
        assertEquals(null, ShipmentMethodUi.fromOrNull("expressly"))
        assertEquals(null, ShipmentMethodUi.fromOrNull("seadropoff"))

        assertEquals("Airdrop", shippingMethodDisplayName("Standard"))
        assertEquals("Airdrop", shippingMethodDisplayName("Airdrop"))
        assertEquals("Freight", shippingMethodDisplayName("Freight"))
        assertEquals("nonstandard", shippingMethodDisplayName("nonstandard"))
        assertEquals("—", shippingMethodDisplayName(null))
        assertEquals("-", shippingMethodDisplayName("", missingValue = "-"))

        assertEquals("Standard", TextSizeController.Level.STANDARD.displayName)
    }
}
