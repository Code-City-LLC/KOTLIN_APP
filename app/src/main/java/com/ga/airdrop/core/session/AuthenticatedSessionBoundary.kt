package com.ga.airdrop.core.session

import com.ga.airdrop.core.auth.AuthTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class AuthenticatedSessionOwner(
    val sessionId: String,
    val accountId: Int? = null,
)

data class AuthenticatedRequestOwner(
    val session: AuthenticatedSessionOwner,
    val provenance: AuthTokenStore.RequestProvenance,
)

enum class AuthenticatedOwnerChange {
    Unchanged,
    IdentityUpdated,
    SessionReplaced,
}

fun AuthenticatedSessionOwner?.changeTo(next: AuthenticatedSessionOwner?): AuthenticatedOwnerChange =
    when {
        this == next -> AuthenticatedOwnerChange.Unchanged
        this?.sessionId == next?.sessionId -> AuthenticatedOwnerChange.IdentityUpdated
        else -> AuthenticatedOwnerChange.SessionReplaced
    }

interface AuthenticatedSessionBoundary {
    val changes: Flow<AuthenticatedSessionOwner?>
    fun capture(): AuthenticatedSessionOwner?
    fun isCurrent(owner: AuthenticatedSessionOwner): Boolean
    fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean
    fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean
    fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner?
    fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean
}

/** Rejects events from an old ViewModel before its session collector has observed replacement. */
fun AuthenticatedSessionBoundary.captureOwnedSession(
    observedOwner: AuthenticatedSessionOwner?,
): AuthenticatedSessionOwner? = capture()?.takeIf { it == observedOwner && isCurrent(it) }

fun AuthenticatedSessionBoundary.captureOwnedRequest(
    observedOwner: AuthenticatedSessionOwner?,
): AuthenticatedRequestOwner? = captureOwnedSession(observedOwner)?.let(::requestOwner)

object DefaultAuthenticatedSessionBoundary : AuthenticatedSessionBoundary {
    override val changes: Flow<AuthenticatedSessionOwner?> =
        AuthTokenStore.snapshotFlow
            .map { snapshot ->
                snapshot.sessionId
                    ?.takeIf { snapshot.token != null }
                    ?.let { AuthenticatedSessionOwner(it, snapshot.accountId) }
            }
            .distinctUntilChanged()

    override fun capture(): AuthenticatedSessionOwner? =
        AuthTokenStore.snapshot().let { snapshot ->
            snapshot.sessionId
                ?.takeIf { snapshot.token != null }
                ?.let { AuthenticatedSessionOwner(it, snapshot.accountId) }
        }

    override fun isCurrent(owner: AuthenticatedSessionOwner): Boolean =
        AuthTokenStore.isCurrentSession(owner.sessionId)

    override fun apply(owner: AuthenticatedSessionOwner, action: () -> Unit): Boolean =
        AuthTokenStore.applyIfCurrentSession(owner.sessionId, action)

    override fun runWhileCurrent(owner: AuthenticatedSessionOwner, action: () -> Boolean): Boolean =
        AuthTokenStore.runWhileCurrentSession(owner.sessionId, owner.accountId, action)

    override fun requestOwner(owner: AuthenticatedSessionOwner): AuthenticatedRequestOwner? {
        val snapshot = AuthTokenStore.snapshot()
        if (snapshot.sessionId != owner.sessionId || snapshot.accountId != owner.accountId) return null
        val provenance = AuthTokenStore.requestProvenance(snapshot) ?: return null
        return AuthenticatedRequestOwner(owner, provenance)
    }

    override fun bindAccountId(owner: AuthenticatedSessionOwner, accountId: Int): Boolean =
        AuthTokenStore.bindAccountId(owner.sessionId, accountId)
}

/** Owns cancellable work for one authenticated session generation. */
class AuthenticatedSessionJobs(private val scope: CoroutineScope) {
    private val parent = scope.coroutineContext[Job]
    private var generation = SupervisorJob(parent)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(generation, block = block)

    fun replaceSession() {
        generation.cancel()
        generation = SupervisorJob(parent)
    }
}
