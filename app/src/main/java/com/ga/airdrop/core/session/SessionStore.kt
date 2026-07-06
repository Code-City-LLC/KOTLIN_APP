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

    // Atomic read-modify-write so concurrent header writers (cart-count,
    // profile, AirCoins refresh) can't lose each other's fields the way
    // `_header.value = transform(_header.value)` did (BUG_AUDIT H30).
    fun update(transform: (HeaderInfo) -> HeaderInfo) {
        _header.update(transform)
    }

    fun clear() {
        _header.value = HeaderInfo()
    }

    fun greetingLine(): String {
        val info = _header.value
        return listOf(info.greeting, info.firstName).filter { it.isNotBlank() }.joinToString(" ")
    }
}
