package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.ActiveDeliveriesPayload
import com.ga.airdrop.data.model.ActiveDeliverySummary
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.DeliveryTracking
import com.ga.airdrop.data.model.DeliveryTrackingPagination
import com.ga.airdrop.data.model.DeliveryTrackingPayload
import com.ga.airdrop.data.model.DeliveryTrackingStage
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

class DeliveryTrackingContractTest {

    @Test
    fun decodesExactLaravelActiveAndDetailEnvelopesInServerOrder() {
        val active = AirdropJson.decodeFromString<DataEnvelope<ActiveDeliveriesPayload>>(
            """
            {
              "success": true,
              "data": {
                "deliveries": [{
                  "package_id": 41,
                  "tracking_code": "AD-0041",
                  "description": "Blue crate",
                  "status": "out_for_delivery",
                  "scheduled_date": "2026-07-22",
                  "current_stage_key": "out_for_delivery",
                  "updated_at": "2026-07-22T13:20:00+00:00"
                }],
                "meta": {"current_page": 1, "per_page": 50, "total": 1, "last_page": 1}
              }
            }
            """.trimIndent(),
        ).data
        val detail = AirdropJson.decodeFromString<DataEnvelope<DeliveryTrackingPayload>>(
            """
            {
              "success": true,
              "data": {
                "package_id": 41,
                "delivery": {
                  "status": "out_for_delivery",
                  "scheduled_date": "2026-07-22",
                  "assigned_at": "2026-07-22T12:00:00+00:00",
                  "out_for_delivery_at": "2026-07-22T13:20:00+00:00",
                  "delivered_at": null,
                  "stages": [
                    {"key":"assigned","label":"Assigned to driver","state":"done","at":"2026-07-22T12:00:00+00:00"},
                    {"key":"out_for_delivery","label":"Driver is on the way","state":"current","at":"2026-07-22T13:20:00+00:00"},
                    {"key":"delivered","label":"Delivered to customer","state":"pending","at":null}
                  ]
                }
              }
            }
            """.trimIndent(),
        ).data

        assertEquals(41, active?.deliveries?.single()?.packageId)
        assertEquals("out_for_delivery", active?.deliveries?.single()?.currentStageKey)
        assertEquals(1, active?.meta?.lastPage)
        assertEquals(41, detail?.packageId)
        assertEquals(
            listOf("Assigned to driver", "Driver is on the way", "Delivered to customer"),
            detail?.delivery?.stages?.map { it.label },
        )
        assertNull(detail?.delivery?.deliveredAt)
    }

    @Test
    fun repositoryUsesExactPaginationAndPreservesServerOwnedStages() = runBlocking {
        val calls = mutableListOf<String>()
        val service = service(
            active = { page, perPage ->
                calls += "active:$page:$perPage"
                DataEnvelope(
                    success = true,
                    data = ActiveDeliveriesPayload(
                        deliveries = listOf(activeWire(41)),
                        meta = DeliveryTrackingPagination(
                            currentPage = page,
                            perPage = perPage,
                            total = 2,
                            lastPage = 2,
                        ),
                    ),
                )
            },
            detail = { packageId ->
                calls += "detail:$packageId"
                DataEnvelope(
                    success = true,
                    data = DeliveryTrackingPayload(
                        packageId = packageId,
                        delivery = DeliveryTracking(
                            status = "out_for_delivery",
                            stages = listOf(
                                stage("accepted", "Accepted by dispatch", "done"),
                                stage("road", "Vehicle departed", "current"),
                                stage("handed_over", "Handed to customer", "pending"),
                            ),
                        ),
                    ),
                )
            },
        )
        val repository = DeliveryTrackingRepository(service)

        val activePage = repository.activeDeliveries(page = 1, perPage = 50).getOrThrow()
        val tracking = repository.deliveryTracking(packageId = 41).getOrThrow()

        assertEquals(listOf("active:1:50", "detail:41"), calls)
        assertEquals(41, activePage.deliveries.single().packageId)
        assertTrue(activePage.hasNextPage)
        assertEquals(
            listOf("Accepted by dispatch", "Vehicle departed", "Handed to customer"),
            tracking.delivery?.stages?.map(TrackedDeliveryStage::label),
        )
    }

    @Test
    fun repositoryFailsClosedForDuplicateActiveRowsOrFabricatedStageState() = runBlocking {
        val duplicateRows = DeliveryTrackingRepository(
            service(
                active = { _, _ ->
                    DataEnvelope(
                        success = true,
                        data = ActiveDeliveriesPayload(
                            deliveries = listOf(activeWire(9), activeWire(9)),
                            meta = DeliveryTrackingPagination(currentPage = 1, lastPage = 1),
                        ),
                    )
                },
            )
        ).activeDeliveries(page = 1, perPage = 50)
        val invalidStage = DeliveryTrackingRepository(
            service(
                detail = { packageId ->
                    DataEnvelope(
                        success = true,
                        data = DeliveryTrackingPayload(
                            packageId = packageId,
                            delivery = DeliveryTracking(
                                status = "assigned",
                                stages = listOf(stage("assigned", "Assigned", "guessed")),
                            ),
                        ),
                    )
                },
            )
        ).deliveryTracking(packageId = 9)

        assertTrue(duplicateRows.isFailure)
        assertTrue(invalidStage.isFailure)
    }

    @Test
    fun nullDeliveryIsAnHonestResultAndApiAnnotationsUseCustomerRoutes() = runBlocking {
        val repository = DeliveryTrackingRepository(
            service(
                detail = { packageId ->
                    DataEnvelope(
                        success = true,
                        data = DeliveryTrackingPayload(packageId = packageId, delivery = null),
                    )
                },
            )
        )

        assertNull(repository.deliveryTracking(17).getOrThrow().delivery)

        val activeMethod = AirdropApiService::class.java.methods.single {
            it.name == "activeDeliveries"
        }
        val detailMethod = AirdropApiService::class.java.methods.single {
            it.name == "packageDeliveryTracking"
        }
        val activeGet = requireNotNull(activeMethod.getAnnotation(GET::class.java))
        val detailGet = requireNotNull(detailMethod.getAnnotation(GET::class.java))
        assertEquals("deliveries/active", activeGet.value)
        assertEquals(
            listOf("page", "per_page"),
            activeMethod.parameterAnnotations
                .flatMap(Array<Annotation>::asList)
                .filterIsInstance<Query>()
                .map(Query::value),
        )
        assertEquals(
            "packages/{id}/delivery-tracking",
            detailGet.value,
        )
        assertEquals(
            listOf("id"),
            detailMethod.parameterAnnotations
                .flatMap(Array<Annotation>::asList)
                .filterIsInstance<Path>()
                .map(Path::value),
        )
    }

    private fun activeWire(packageId: Int) = ActiveDeliverySummary(
        packageId = packageId,
        trackingCode = "AD-$packageId",
        description = "Package $packageId",
        status = "assigned",
        currentStageKey = "assigned",
    )

    private fun stage(key: String, label: String, state: String) = DeliveryTrackingStage(
        key = key,
        label = label,
        state = state,
    )

    @Suppress("UNCHECKED_CAST")
    private fun service(
        active: (Int, Int) -> DataEnvelope<ActiveDeliveriesPayload> = { _, _ ->
            error("Unexpected active-deliveries call")
        },
        detail: (Int) -> DataEnvelope<DeliveryTrackingPayload> = {
            error("Unexpected delivery-tracking call")
        },
    ): AirdropApiService = Proxy.newProxyInstance(
        AirdropApiService::class.java.classLoader,
        arrayOf(AirdropApiService::class.java),
    ) { _, method, args ->
        when (method.name) {
            "activeDeliveries" -> active(args?.get(0) as Int, args[1] as Int)
            "packageDeliveryTracking" -> detail(args?.get(0) as Int)
            else -> error("Unexpected service call: ${method.name}")
        }
    } as AirdropApiService
}
