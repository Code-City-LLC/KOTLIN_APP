package com.ga.airdrop.feature.more2

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.feature.shop.AffiliatePromotionProductsSource
import com.ga.airdrop.feature.shop.RepositoryAffiliatePromotionProductsSource
import com.ga.airdrop.feature.shop.ShopProduct
import com.ga.airdrop.feature.shop.completeSalePromotionProducts
import com.ga.airdrop.feature.shop.eligibleAmazonAppleProducts
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val PROMOTIONS_ALL_FEEDS_ERROR =
    "We couldn't load promotions. Check your connection and try again."

data class PromotionsUiState(
    val banners: List<PromotionalBanner> = emptyList(),
    val amazonFinds: List<ShopProduct> = emptyList(),
    val saleHighlights: List<ShopProduct> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val hasLoaded: Boolean = false,
)

interface PromotionsFeeds {
    suspend fun banners(): Result<List<PromotionalBanner>>
    suspend fun featuredProducts(): Result<List<ShopProduct>>
    suspend fun saleProducts(): Result<List<ShopProduct>>
}

private class RepositoryPromotionsFeeds(
    private val bannersRepository: More2Repository = More2Repository(),
    private val products: AffiliatePromotionProductsSource =
        RepositoryAffiliatePromotionProductsSource(),
) : PromotionsFeeds {
    override suspend fun banners(): Result<List<PromotionalBanner>> =
        bannersRepository.promotionalBanners(activeOnly = true)

    override suspend fun featuredProducts(): Result<List<ShopProduct>> =
        products.featuredProducts()

    override suspend fun saleProducts(): Result<List<ShopProduct>> =
        products.saleProducts()
}

/**
 * Three independent canonical feeds. Any successful feed produces usable UI;
 * only a failure from all three enters the retryable global error state.
 */
class PromotionsViewModel(
    private val feeds: PromotionsFeeds = RepositoryPromotionsFeeds(),
) : ViewModel() {

    private val _state = MutableStateFlow(PromotionsUiState())
    val state: StateFlow<PromotionsUiState> = _state

    init {
        load()
    }

    fun load() {
        if (_state.value.loading) return
        val previous = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val results = coroutineScope {
                val banners = async { safeFeed(feeds::banners) }
                val featured = async { safeFeed(feeds::featuredProducts) }
                val sales = async { safeFeed(feeds::saleProducts) }
                Triple(banners.await(), featured.await(), sales.await())
            }
            val (bannersResult, featuredResult, salesResult) = results
            val anySuccess =
                bannersResult.isSuccess || featuredResult.isSuccess || salesResult.isSuccess

            if (!anySuccess) {
                _state.update {
                    previous.copy(
                        loading = false,
                        hasLoaded = true,
                        error = PROMOTIONS_ALL_FEEDS_ERROR,
                    )
                }
                return@launch
            }

            _state.update {
                PromotionsUiState(
                    banners = bannersResult.getOrElse { previous.banners },
                    amazonFinds = featuredResult
                        .map { eligibleAmazonAppleProducts(it, limit = 6) }
                        .getOrElse { previous.amazonFinds },
                    saleHighlights = salesResult
                        .map { completeSalePromotionProducts(it, limit = 4) }
                        .getOrElse { previous.saleHighlights },
                    loading = false,
                    error = null,
                    hasLoaded = true,
                )
            }
        }
    }

    private suspend fun <T> safeFeed(block: suspend () -> Result<T>): Result<T> =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            Result.failure(failure)
        }
}
