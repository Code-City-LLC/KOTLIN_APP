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

    data class UserIdentity(
        val accountNumber: String? = null,
        val userId: Int? = null,
    ) {
        val redeemAccount: String?
            get() = accountNumber?.trim()?.takeIf { it.isNotEmpty() }
                ?: userId?.takeIf { it > 0 }?.toString()
    }

    private val _header = MutableStateFlow(HeaderInfo())
    val header: StateFlow<HeaderInfo> = _header

    private val _identity = MutableStateFlow(UserIdentity())
    val identity: StateFlow<UserIdentity> = _identity

    // Atomic read-modify-write so concurrent header writers (cart-count,
    // profile, AirCoins refresh) can't lose each other's fields the way
    // `_header.value = transform(_header.value)` did (BUG_AUDIT H30).
    fun update(transform: (HeaderInfo) -> HeaderInfo) {
        _header.update(transform)
    }

    fun updateIdentity(accountNumber: String?, userId: Int?) {
        _identity.update { current ->
            current.copy(
                accountNumber = accountNumber?.trim()?.takeIf { it.isNotEmpty() }
                    ?: current.accountNumber,
                userId = userId?.takeIf { it > 0 } ?: current.userId,
            )
        }
    }

    fun clear() {
        _header.value = HeaderInfo()
        _identity.value = UserIdentity()
    }

    fun greetingLine(): String {
        val info = _header.value
        return listOf(info.greeting, info.firstName).filter { it.isNotBlank() }.joinToString(" ")
    }
}
