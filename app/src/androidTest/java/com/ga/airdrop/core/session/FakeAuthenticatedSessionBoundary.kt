package com.ga.airdrop.core.session

import com.ga.airdrop.core.auth.AuthTokenStore
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicInteger

class FakeAuthenticatedSessionBoundary(
    initialSessionId: String? = "session-a",
    initialAccountId: Int? = 1,
) :
    AuthenticatedSessionBoundary {

    private val initialOwner = initialSessionId?.let { AuthenticatedSessionOwner(it, initialAccountId) }
    private val currentOwner = AtomicReference(initialOwner)
    private val revision = AtomicLong(1L)
    private val ownerFlow = MutableStateFlow(initialOwner)
    override val changes: Flow<AuthenticatedSessionOwner?> = ownerFlow
    val rejectedApplyAttempts = AtomicInteger()

    init {
        SessionStore.onAuthenticatedSessionChanged(initialOwner?.sessionId)
    }

    @Synchronized
    override fun capture(): AuthenticatedSessionOwner? = currentOwner.get()

    @Synchronized
    override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean =
        currentOwner.get()?.sessionId == owner.sessionId

    @Synchronized
    override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean {
        if (currentOwner.get()?.sessionId != owner.sessionId) {
            rejectedApplyAttempts.incrementAndGet()
            return false
        }
        action()
        return true
    }

    @Synchronized
    override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean {
        if (currentOwner.get() != owner) {
            rejectedApplyAttempts.incrementAndGet()
            return false
        }
        val actionSucceeded = action()
        val stillOwned = currentOwner.get() == owner
        if (!stillOwned) rejectedApplyAttempts.incrementAndGet()
        return actionSucceeded && stillOwned
    }

    @Synchronized
    override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? {
        if (currentOwner.get() != owner) return null
        return AuthenticatedRequestOwner(
            session = owner,
            provenance = AuthTokenStore.RequestProvenance(revision.get(), owner.sessionId, owner.accountId),
        )
    }

    @Synchronized
    fun replace(sessionId: String?, accountId: Int? = currentOwner.get()?.accountId) {
        replaceCurrent(sessionId, accountId)
        emitCurrent()
    }

    @Synchronized
    fun replaceCurrent(sessionId: String?, accountId: Int? = currentOwner.get()?.accountId) {
        currentOwner.set(sessionId?.let { AuthenticatedSessionOwner(it, accountId) })
        revision.incrementAndGet()
        SessionStore.onAuthenticatedSessionChanged(sessionId)
    }

    @Synchronized
    fun emitCurrent() {
        ownerFlow.value = currentOwner.get()
    }

    @Synchronized
    fun isCurrent(request: AuthenticatedRequestOwner): Boolean =
        currentOwner.get() == request.session && revision.get() == request.provenance.revision

    fun emittedOwner(): AuthenticatedSessionOwner? = ownerFlow.value

    fun currentRevision(): Long = revision.get()

    fun currentSessionId(): String? = currentOwner.get()?.sessionId

    fun currentRequestOwner(): AuthenticatedRequestOwner? =
        currentOwner.get()?.let(::requestOwner)

    @Synchronized
    override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean {
        if (currentOwner.get() != owner || accountId <= 0) return false
        currentOwner.set(owner.copy(accountId = accountId))
        ownerFlow.value = currentOwner.get()
        return true
    }
}
