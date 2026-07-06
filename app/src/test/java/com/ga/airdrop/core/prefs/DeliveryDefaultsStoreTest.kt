package com.ga.airdrop.core.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Feature D — default delivery method. Plain-JVM tests:
 *  - enum contract (raw keys + display names) is the Swift/UserDefaults wire
 *    format, so pin it against a drive-by rename;
 *  - [DeliveryDefaultsStore.Method.fromRaw] round-trips and rejects junk;
 *  - the guarded-lateinit pattern (C7/C8) holds — reads/writes before `init()`
 *    no-op instead of throwing `UninitializedPropertyAccessException`.
 */
class DeliveryDefaultsStoreTest {

    @Test
    fun `enum raw keys and display names match Swift`() {
        assertEquals("pickup", DeliveryDefaultsStore.Method.PICKUP.raw)
        assertEquals("homeDelivery", DeliveryDefaultsStore.Method.HOME.raw)
        assertEquals("Counter pickup", DeliveryDefaultsStore.Method.PICKUP.displayName)
        assertEquals("Home delivery", DeliveryDefaultsStore.Method.HOME.displayName)
    }

    @Test
    fun `fromRaw round-trips and rejects unknown or null`() {
        assertEquals(DeliveryDefaultsStore.Method.PICKUP, DeliveryDefaultsStore.Method.fromRaw("pickup"))
        assertEquals(DeliveryDefaultsStore.Method.HOME, DeliveryDefaultsStore.Method.fromRaw("homeDelivery"))
        assertNull(DeliveryDefaultsStore.Method.fromRaw("bogus"))
        assertNull(DeliveryDefaultsStore.Method.fromRaw(null))
    }

    @Test
    fun `reads and writes before init are safe no-ops`() {
        // init() is never called, so ::prefs stays unbound. A completing test
        // proves the guard holds: getter is null, setter and clearAll no-op.
        assertNull(DeliveryDefaultsStore.preferredMethod)
        DeliveryDefaultsStore.preferredMethod = DeliveryDefaultsStore.Method.HOME
        assertNull(DeliveryDefaultsStore.preferredMethod)
        DeliveryDefaultsStore.clearAll()
    }
}
