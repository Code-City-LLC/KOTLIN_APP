package com.ga.airdrop.feature.homedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.core.session.AuthenticatedSessionBoundary
import com.ga.airdrop.core.session.AuthenticatedSessionOwner
import com.ga.airdrop.core.session.DefaultAuthenticatedSessionBoundary
import com.ga.airdrop.core.session.captureOwnedRequest
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.BusinessRateInquiryRequest
import com.ga.airdrop.data.repo.BusinessRateInquiryRepository
import com.ga.airdrop.data.repo.BusinessRateInquirySender
import com.ga.airdrop.data.repo.UserRepository
import com.ga.airdrop.feature.contacts.BusinessInquiryChat
import com.ga.airdrop.feature.contacts.LiveAgentChatRepository
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class BusinessStage { Overview, Form, Success }
internal enum class BusinessPhase { Ready, LoadingProfile, ProfileFailed, Sending, Failed, Expired }
internal data class BusinessInquiryState(
    val stage: BusinessStage = BusinessStage.Overview,
    val phase: BusinessPhase = BusinessPhase.Ready,
    val businessName: String = "",
    val airDropPounds: String = "",
    val seaDropCubicFeet: String = "",
    val account: String? = null,
    val error: String? = null,
    val needsConsent: Boolean = false,
    val conversationId: String? = null,
    val alreadySubmitted: Boolean = false,
) {
    val busy get() = phase == BusinessPhase.LoadingProfile || phase == BusinessPhase.Sending
}

/** One sheet owns overview -> form -> confirmed inbox/chat. No alternate send rail. */
internal class BusinessInquiryViewModel(
    private val profile: suspend () -> AirdropUser = { UserRepository(ApiClient.service).currentUser().getOrThrow() },
    private val inbox: BusinessRateInquirySender = BusinessRateInquiryRepository(ApiClient.service),
    private val chatFactory: (AuthTokenStore.RequestProvenance) -> BusinessInquiryChat = {
        LiveAgentChatRepository(expectedSession = it)
    },
    private val boundary: AuthenticatedSessionBoundary = DefaultAuthenticatedSessionBoundary,
    private val requestId: String = UUID.randomUUID().toString(),
) : ViewModel() {
    private val _state = MutableStateFlow(BusinessInquiryState())
    val state: StateFlow<BusinessInquiryState> = _state
    private var owner = boundary.capture()
    private var user: AirdropUser? = null
    private var work: Job? = null

    init {
        viewModelScope.launch {
            boundary.changes.collect { next ->
                if (next?.sessionId != owner?.sessionId ||
                    (owner?.accountId != null && next?.accountId != owner?.accountId)) {
                    work?.cancel()
                    user = null
                    _state.value = BusinessInquiryState(
                        phase = BusinessPhase.Expired,
                        error = "Your account changed. Close and reopen your Business request.",
                    )
                } else owner = next
            }
        }
    }

    fun openForm() {
        if (_state.value.busy || _state.value.phase == BusinessPhase.Expired) return
        _state.update { it.copy(stage = BusinessStage.Form, error = null) }
        if (user == null) loadProfile()
    }

    fun back() {
        if (!_state.value.busy) _state.update { it.copy(stage = BusinessStage.Overview, needsConsent = false, error = null) }
    }

    fun edit(name: String = _state.value.businessName, pounds: String = _state.value.airDropPounds,
             cubicFeet: String = _state.value.seaDropCubicFeet) {
        if (!_state.value.busy && !_state.value.needsConsent && _state.value.phase != BusinessPhase.Expired) {
            _state.update { it.copy(businessName = name, airDropPounds = pounds, seaDropCubicFeet = cubicFeet, error = null) }
        }
    }

    fun loadProfile() {
        if (_state.value.busy || _state.value.phase == BusinessPhase.Expired) return
        val captured = boundary.captureOwnedRequest(owner)?.session ?: return expire()
        _state.update { it.copy(phase = BusinessPhase.LoadingProfile, error = null) }
        work = viewModelScope.launch {
            try {
                val loaded = profile()
                ensureOwner(captured)
                val id = loaded.id ?: error("Your profile is incomplete. Please try again.")
                if (captured.accountId == null) {
                    check(boundary.bindAccountId(captured, id)) { "Your account changed." }
                    owner = boundary.capture()?.takeIf { it.sessionId == captured.sessionId && it.accountId == id }
                        ?: throw CancellationException()
                } else check(captured.accountId == id) { "Your account changed. Close and reopen your Business request." }
                user = loaded
                _state.update {
                    it.copy(phase = BusinessPhase.Ready, account = loaded.accountNumber,
                        businessName = it.businessName.ifBlank {
                            listOfNotNull(loaded.firstName, loaded.lastName).joinToString(" ").trim()
                        })
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                if (isOwned(captured)) _state.update {
                    it.copy(phase = BusinessPhase.ProfileFailed, error = "We couldn’t load your profile. Your request was not sent. Tap Retry.")
                }
            }
        }
    }

    fun declineConsent() {
        _state.update { it.copy(needsConsent = false, phase = BusinessPhase.Failed,
            error = "Your request was not sent. Continue when you’re ready to use chat.") }
    }

    fun acceptConsent(rememberConsent: () -> Unit) {
        val captured = boundary.captureOwnedRequest(owner)?.session ?: return expire()
        if (!_state.value.needsConsent) return
        if (!boundary.runWhileCurrent(captured) { rememberConsent(); true }) return expire()
        submit(consentGranted = true)
    }

    fun submit(consentGranted: Boolean) {
        val snapshot = _state.value
        if (snapshot.busy || snapshot.phase == BusinessPhase.Expired || snapshot.stage != BusinessStage.Form) return
        if (user == null) { loadProfile(); return }
        val request = try { validatedInquiry(snapshot, requestId) } catch (e: IllegalArgumentException) {
            _state.update { it.copy(error = e.message) }; return
        }
        val captured = boundary.captureOwnedRequest(owner) ?: return expire()
        val customer = user ?: return
        if (captured.session.accountId != customer.id) return expire()
        if (!consentGranted) { _state.update { it.copy(needsConsent = true, error = null) }; return }
        _state.update { it.copy(phase = BusinessPhase.Sending, needsConsent = false, error = null) }
        work = viewModelScope.launch {
            var stored = false
            try {
                val chat = chatFactory(captured.provenance)
                ensureOwner(captured.session)
                val session = chat.startSession(customer)
                ensureOwner(captured.session)
                var messages = session.messages
                try { messages = chat.messages(session.conversationId) } catch (e: CancellationException) { throw e
                } catch (_: Exception) { /* Use the session's known history, as Swift does. */ }
                ensureOwner(captured.session)
                val details = inquiryDetails(request)
                val customers = messages.filter { it.isCustomerAuthored }
                val duplicate = customers.any { normalizedInquiry(it.body) == normalizedInquiry(details) }
                val previous = customers.any { it.body.contains("Business-rate inquiry", ignoreCase = true) }
                // Re-capture the current token revision only within this same account/session.
                val dispatch = boundary.captureOwnedRequest(owner) ?: throw CancellationException()
                ensureOwner(captured.session)
                val confirmation = inbox.submit(request, dispatch.provenance).getOrThrow()
                stored = true
                ensureOwner(captured.session)
                var conversation = session.conversationId
                if (!duplicate) {
                    val updating = previous || confirmation.action == "updated"
                    val subject = if (updating) "Business-rate inquiry update — follow up now."
                        else "Business-rate inquiry — follow up now."
                    val result = chat.sendInquiry(conversation, "$subject\n\n$details", customer,
                        inquiryMetadata(request) + mapOf(
                            "request_action" to if (updating) "update" else "create",
                            "inbox_action" to confirmation.action,
                            "laravel_message_id" to confirmation.messageId.toString(),
                        ))
                    conversation = result.conversationId?.takeIf { it.isNotBlank() } ?: conversation
                }
                ensureOwner(captured.session)
                _state.update { it.copy(stage = BusinessStage.Success, phase = BusinessPhase.Ready,
                    conversationId = conversation, alreadySubmitted = duplicate) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                if (isOwned(captured.session)) _state.update {
                    it.copy(phase = BusinessPhase.Failed, error = if (stored)
                        "Your request reached the support inbox, but chat did not finish. Tap Try Again."
                    else "We couldn’t send your Business request. Tap Try Again.")
                }
            }
        }
    }

    private fun isOwned(captured: AuthenticatedSessionOwner) = boundary.capture() == captured && boundary.isCurrent(captured)
    private fun ensureOwner(captured: AuthenticatedSessionOwner) { if (!isOwned(captured)) throw CancellationException() }
    private fun expire() { _state.update { it.copy(phase = BusinessPhase.Expired, needsConsent = false,
        error = "Your account changed. Close and reopen your Business request.") } }
    fun close() { viewModelScope.cancel() }
}

internal fun validatedInquiry(state: BusinessInquiryState, requestId: String): BusinessRateInquiryRequest {
    val name = state.businessName.trim()
    require(name.isNotEmpty()) { "Enter your business name." }
    require(name.length <= 160) { "Use no more than 160 characters for your business name." }
    fun quantity(raw: String): Double {
        val text = raw.trim().replace(",", "")
        if (text.isEmpty()) return 0.0
        val number = text.toDoubleOrNull()
        require(number != null && number.isFinite() && number >= 0) { "Use numbers only for pounds and cubic feet." }
        require(number <= 1_000_000) { "Monthly volumes must be no more than 1,000,000." }
        return number
    }
    val air = quantity(state.airDropPounds); val sea = quantity(state.seaDropCubicFeet)
    require(air >= 400 || sea >= 106.7) { "Enter either at least 400 lb/month for Airdrop or 106.7 ft³/month for SeaDrop." }
    return BusinessRateInquiryRequest(name, requestId, air.takeIf { it > 0 }, sea.takeIf { it > 0 })
}

private fun quantity(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.1f", value)
internal fun inquiryDetails(request: BusinessRateInquiryRequest): String {
    val air = request.airDropMonthlyPounds?.let { "${quantity(it)} lb/month" } ?: "Not provided"
    val sea = request.seaDropMonthlyCubicFeet?.let { "${quantity(it)} ft³/month (about ${quantity(it / (48.0 * 40 * 48 / 1728))} loaded pallet-equivalents)" } ?: "Not provided"
    return """
        Hi AirDrop, I’m interested in Business shipping rates.

        Business name: ${request.businessName}
        Airdrop monthly volume: $air
        SeaDrop monthly volume: $sea
        SeaDrop reference pallet: 48 × 40 × 48 in (53.3 ft³)

        I consistently ship approximately the volume listed above. Please follow up with me now about eligibility and a custom rate.
    """.trimIndent()
}
internal fun inquiryMetadata(request: BusinessRateInquiryRequest): Map<String, String> = buildMap {
    put("request_type", "business_rate_inquiry"); put("priority", "high"); put("follow_up_requested", "now")
    put("business_name", request.businessName); put("client_request_id", request.clientRequestId)
    put("seadrop_pallet_dimensions_in", "48 × 40 × 48 in"); put("seadrop_cubic_feet_per_pallet", "53.3")
    request.airDropMonthlyPounds?.let { put("airdrop_monthly_lb", quantity(it)) }
    request.seaDropMonthlyCubicFeet?.let {
        put("seadrop_monthly_cubic_feet", quantity(it))
        put("seadrop_monthly_pallet_equivalents", quantity(it / (48.0 * 40 * 48 / 1728)))
    }
}
internal fun normalizedInquiry(body: String): String = body.lines().let {
    if (it.firstOrNull()?.contains("Business-rate inquiry", ignoreCase = true) == true) it.drop(1) else it
}.joinToString(" ").trim().split(Regex("\\s+")).joinToString(" ").lowercase(Locale.ROOT)
