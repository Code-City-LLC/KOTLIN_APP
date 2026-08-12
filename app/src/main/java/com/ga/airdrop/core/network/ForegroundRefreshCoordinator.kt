package com.ga.airdrop.core.network

import kotlinx.coroutines.sync.Mutex

/**
 * Serializes the foreground bearer rotation with the notification-badge probe.
 *
 * Laravel invalidates the old Sanctum token while `/auth/refresh` is running.
 * If the badge probe leaves with that old bearer at the same time, its 401 can
 * attempt a second refresh against an already-deleted token and sign out a
 * healthy customer. Both callers use this narrow coordinator so either the
 * probe finishes first or it waits and uses the rotated bearer.
 */
internal object ForegroundRefreshCoordinator {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
