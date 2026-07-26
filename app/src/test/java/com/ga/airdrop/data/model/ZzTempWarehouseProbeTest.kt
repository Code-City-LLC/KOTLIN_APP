package com.ga.airdrop.data.model

import com.ga.airdrop.data.api.AirdropJson
import org.junit.Test

class ZzTempWarehouseProbeTest {

    private val live = """
    {"success":true,"data":{"address_line_1":"6175 NW 167th Street, Unit G36",
    "street":"6175 NW 167th Street","unit":"Unit G36","city":"Hialeah","state":"Florida",
    "zip_code":"33015","phone_number":"1(954)508-1797",
    "address_line_2_tokens":{"standard":"AIR","express":"EXPRESS","seadrop":"SEA"},
    "address_line_2_separator":" - ","warehouse":{"id":1,"name":"Miami"}},
    "meta":{"timestamp":"x","version":"v1"}}
    """.trimIndent()

    @Test
    fun probe() {
        val old = AirdropJson.decodeFromString(
            Paginated.serializer(Warehouse.serializer()), live,
        )
        println("PROBE_OLD_ITEMS_SIZE=${old.items.size}")

        val new = AirdropJson.decodeFromString(
            DataEnvelope.serializer(Warehouse.serializer()), live,
        )
        println("PROBE_NEW_DATA=${new.data}")
        println("PROBE_NEW_UNIT=${new.data?.unit}")
        println("PROBE_NEW_TOKENS=${new.data?.addressLine2Tokens}")
        println("PROBE_NEW_CITY=${new.data?.city}")
        println("PROBE_NEW_ADDR=${new.data?.address}")
        println("PROBE_NEW_PHONE=${new.data?.phoneNumber}")
    }
}
