package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.api.toUserMessage
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.feature.shop.ShopProduct
import com.ga.airdrop.feature.shop.ShopProductsRepository
import com.ga.airdrop.feature.shop.ShopRepoProvider
import com.ga.airdrop.feature.shop.eligibleAppleAmazonProducts
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PromotionsUiState(
    val banners: List<PromotionalBanner> = emptyList(),
    val appleFinds: List<ShopProduct> = emptyList(),
    val saleHighlights: List<ShopProduct> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false,
)

/**
 * Swift Promotions parity: existing banners plus Apple Amazon finds from
 * /featured-products and real AirDrop Sale Highlights from /products.
 */
class PromotionsViewModel(
    private val repository: More2Repository = More2Repository(),
    private val products: ShopProductsRepository = ShopRepoProvider.products,
) : ViewModel() {

    private val _state = MutableStateFlow(PromotionsUiState())
    val state: StateFlow<PromotionsUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val results = coroutineScope {
                val banners = async { repository.promotionalBanners(activeOnly = true) }
                val featured = async {
                    products.featuredProducts(page = 1, perPage = 50, search = null)
                }
                val sale = async {
                    products.auctionProducts(page = 1, perPage = 4, search = null)
                }
                Triple(banners.await(), featured.await(), sale.await())
            }

            val banners = results.first.getOrDefault(emptyList())
            val appleFinds = eligibleAppleAmazonProducts(results.second.getOrDefault(emptyList()))
            val saleHighlights = results.third.getOrDefault(emptyList())
            val hasContent = banners.isNotEmpty() || appleFinds.isNotEmpty() || saleHighlights.isNotEmpty()
            val firstFailure = listOfNotNull(
                results.first.exceptionOrNull(),
                results.second.exceptionOrNull(),
                results.third.exceptionOrNull(),
            ).firstOrNull()

            _state.update {
                it.copy(
                    banners = banners,
                    appleFinds = appleFinds,
                    saleHighlights = saleHighlights,
                    loading = false,
                    hasLoaded = true,
                    error = if (!hasContent) firstFailure?.toUserMessage() else null,
                )
            }
        }
    }
}
