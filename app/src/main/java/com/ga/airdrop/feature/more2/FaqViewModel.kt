package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.FaqItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Same 14 entries the legacy FAQViewController exposed — the cold-start /
// offline fallback until GET /faqs responds (Swift parity).
private val FALLBACK_FAQS = listOf(
    FaqItem(
        id = "1",
        question = "How do I sign up for an AirDrop account?",
        answer = "Our Sign-Up process is fast, easy and free. Click on SignUp button to get " +
            "started. We’ll send you your very own US shipping address. After signing up you " +
            "can visit our location to formalize your account.",
    ),
    FaqItem(
        id = "2",
        question = "How much will I pay for the weight of my package?",
        answer = "Airdrop charges you based on the weight of your package, please refer to " +
            "our rate sheet.",
    ),
    FaqItem(
        id = "3",
        question = "Is there a weight limit or restriction on any item?",
        answer = "With Airdrop, you don’t have to worry about item size or weight. We’ll " +
            "carry almost anything! There may be specific import regulations which may " +
            "prevent importation of certain items. Some items may also require a " +
            "government-issued permit.",
    ),
    FaqItem(
        id = "4",
        question = "When will I receive my package?",
        answer = "Once your package is received at our Fort Lauderdale warehouse, our " +
            "transit time is 2-4 business days. Barring no delays or hold at customs.",
    ),
    FaqItem(
        id = "5",
        question = "What is the mailing address?",
        answer = "Full name: John Brown\nAddress Line 1: 1905 NW 51st Street 43A\n" +
            "Address Line 2: AIR#\nCity: Fort Lauderdale\nState: Florida\nZip Code: 33309\n" +
            "Phone Number: 954-692-6659\n\nTo ensure your delivery, be sure to include your " +
            "AIR Number – in your address Line 2. And remember to PreAlert all packages",
    ),
    FaqItem(
        id = "6",
        question = "What is CIF value?",
        answer = "CIF means, Cost, Insurance and Freight.",
    ),
    FaqItem(
        id = "7",
        question = "Will I be charged customs duties on my packages?",
        answer = "Yes, all packages entering the country are liable to be charged duties " +
            "and taxes by the customs department.",
    ),
    FaqItem(
        id = "8",
        question = "How do I return a package?",
        answer = "Packages received through Airdrop Ltd. can be returned to the U.S only " +
            "(conditions apply). You are required to contact your seller or supplier for a " +
            "supplier’s RMA. Airdrop must receive all required documents and merchandise no " +
            "later than 15 business days prior to the return authorization’s expiration " +
            "date. The requirements are:\n\nA Return Authorization issued by shipper or " +
            "manufacturer\nThe original invoice\nThe package",
    ),
    FaqItem(
        id = "9",
        question = "What are your opening hours?",
        answer = "Monday – Friday: 9am – 6pm\nSaturday: 10am – 4pm\nSunday: Closed",
    ),
    FaqItem(
        id = "10",
        question = "How do I attach my invoice?",
        answer = "Log into your Airdrop account, click on the ‘Track’ packages menu, select " +
            "your package and go to ‘UPLOAD INVOICE’ to upload the document.",
    ),
    FaqItem(
        id = "11",
        question = "How do I place an order with Airdrop?",
        answer = "You can visit our store, or you can send your orders via email or WhatsApp " +
            "(conditions apply). Contact us if you have any questions.",
    ),
    FaqItem(
        id = "12",
        question = "How do I prealert my package?",
        answer = "Log into your airdrop account, look for the prealert button at home, and " +
            "enter all the required information, it’s as easy as ABC! Prealert helps to " +
            "expedite the processing of your packages.",
    ),
    FaqItem(
        id = "13",
        question = "Is there anything I cannot receive through my Airdrop account?",
        answer = "Items marked as dangerous/ hazardous or illegal by Aviation regulations " +
            "and customs department. If you are unsure, please contact us before shipping " +
            "such items.",
    ),
    FaqItem(
        id = "14",
        question = "Are my packages insured?",
        answer = "Yes, all packages are insured at USD $1.50 for each USD $100.00 declared " +
            "value or portion thereof. We do not accept liability for glass, ceramic, " +
            "packages that are not packed properly and monies of any kind. A USD $1.00 " +
            "Fuel surcharge will be applicable to all shipments.",
    ),
)

data class FaqUiState(
    val faqs: List<FaqItem> = FALLBACK_FAQS,
    // None expanded on cold open, mirroring RN.
    val expandedIds: Set<String> = emptySet(),
)

/** FigmaFAQViewController: fallback list + GET /faqs swap-in accordion. */
class FaqViewModel(
    private val repository: More2Repository = More2Repository(),
) : ViewModel() {

    private val _state = MutableStateFlow(FaqUiState())
    val state: StateFlow<FaqUiState> = _state

    init {
        viewModelScope.launch {
            repository.faqs().onSuccess { remote ->
                if (remote.isEmpty()) return@onSuccess
                val validIds = remote.map { it.id }.toSet()
                _state.update {
                    it.copy(faqs = remote, expandedIds = it.expandedIds intersect validIds)
                }
            }
            // Fetch failures are silent — the fallback stays (Swift parity).
        }
    }

    fun toggle(id: String) = _state.update {
        it.copy(
            expandedIds = if (id in it.expandedIds) it.expandedIds - id else it.expandedIds + id,
        )
    }
}
