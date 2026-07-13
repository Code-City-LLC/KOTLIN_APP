package com.ga.airdrop.core.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory session cache shared by the tab headers — Android counterpart of
 * the Swift singletons (cached user in FigmaTabHeader, FigmaCartStore).
 * Populated on Home load and after auth; cleared on logout.
 */
object SessionStore {

    data class HeaderInfo(
        val greeting: String = "",
        val firstName: String = "",
        val tierName: String = "",
        val airCoins: String = "",
        val cartCount: Int = 0,
    )

    private val _header = MutableStateFlow(HeaderInfo())
    val header: StateFlow<HeaderInfo> = _header
    private val sessionLock = Any()
    private var sessionId: String? = null

    /** Process/auth initialization always starts with an empty account cache. */
    fun initializeAuthenticatedSession(initialSessionId: String?) = synchronized(sessionLock) {
        sessionId = initialSessionId
        _header.value = HeaderInfo()
    }

    /** The sole memory-only auth transition hook, called synchronously by AuthTokenStore. */
    fun onAuthenticatedSessionChanged(newSessionId: String?) = synchronized(sessionLock) {
        if (sessionId == newSessionId) return
        sessionId = newSessionId
        _header.value = HeaderInfo()
    }

    /** Applies only to the session already established by the central auth transition owner. */
    fun updateForSession(
        owner: AuthenticatedSessionOwner,
        transform: (HeaderInfo) -> HeaderInfo,
    ): Boolean = synchronized(sessionLock) {
        if (sessionId != owner.sessionId) return false
        _header.update(transform)
        true
    }

    // Atomic read-modify-write so concurrent header writers (cart-count,
    // profile, AirCoins refresh) can't lose each other's fields the way
    // `_header.value = transform(_header.value)` did (BUG_AUDIT H30).
    fun update(transform: (HeaderInfo) -> HeaderInfo) {
        _header.update(transform)
    }

    fun clear() {
        synchronized(sessionLock) {
            sessionId = null
            _header.value = HeaderInfo()
        }
    }

    fun greetingLine(): String {
        val info = _header.value
        return listOf(info.greeting, info.firstName).filter { it.isNotBlank() }.joinToString(" ")
    }
}
