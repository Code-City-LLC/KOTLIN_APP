package com.ga.airdrop.core.push

import android.content.Intent
import com.ga.airdrop.core.navigation.resolveAirdropRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pending push navigation, consumed by AppRoot once the nav graph is up.
 * Android counterpart of FigmaRouteResolver.push(route:referenceID:).
 */
object PushDeepLink {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending

    fun capture(intent: Intent?) {
        val route = intent?.getStringExtra(AirdropMessagingService.EXTRA_ROUTE) ?: return
        val referenceId = intent.getStringExtra(AirdropMessagingService.EXTRA_REFERENCE_ID)
        _pending.value = resolveAirdropRoute(route, referenceId)
    }

    fun consume(): String? = _pending.value.also { _pending.value = null }
}
