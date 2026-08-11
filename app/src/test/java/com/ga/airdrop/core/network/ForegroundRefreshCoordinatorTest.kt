package com.ga.airdrop.core.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRefreshCoordinatorTest {

    @Test
    fun `badge probe waits until foreground token rotation releases the gate`() = runBlocking {
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val foreground = launch {
            ForegroundRefreshCoordinator.run {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val badge = launch {
            ForegroundRefreshCoordinator.run {
                secondEntered.complete(Unit)
            }
        }
        yield()
        assertFalse("badge network call must not overlap bearer rotation", secondEntered.isCompleted)

        releaseFirst.complete(Unit)
        joinAll(foreground, badge)
        assertTrue("badge network call resumes after rotation", secondEntered.isCompleted)
    }
}
