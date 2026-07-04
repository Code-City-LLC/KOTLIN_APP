package com.ga.airdrop.feature.more2

import com.ga.airdrop.core.network.ApiClient
import com.ga.airdrop.data.api.AirdropJson
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuthorizedUserEnvelope
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsers
import com.ga.airdrop.data.model.AuthorizedUsersEnvelope
import com.ga.airdrop.data.model.CmsContentResponse
import com.ga.airdrop.data.model.DataEnvelope
import com.ga.airdrop.data.model.DeactivateAccountRequest
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.FaqItem
import com.ga.airdrop.data.model.LoginRequest
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.Paginated
import com.ga.airdrop.data.model.PromotionalBanner
import com.ga.airdrop.data.model.ReferFriendRequest
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.data.model.ShippingRates
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/*
 * MORE-group part 2 endpoints, mirroring Swift AirdropAPI / APIEndpoints.
 *
 * RECONCILE: fold these calls into data/api/AirdropApiService (being built in
 * parallel by the data-layer owner) and replace More2Repository with the
 * shared data/repo classes once they land. Models are already the shared ones
 * from data/model.
 */
interface More2Api {

    // ── Authorized users ──
    @GET("authorized-users")
    suspend fun authorizedUsers(): AuthorizedUsersEnvelope

    @GET("authorized-users/{id}")
    suspend fun authorizedUser(@Path("id") id: Int): AuthorizedUserEnvelope

    @POST("authorized-users")
    suspend fun addAuthorizedUser(@Body body: AuthorizedUserRequest): AuthorizedUserEnvelope

    @PUT("authorized-users/{id}")
    suspend fun updateAuthorizedUser(
        @Path("id") id: Int,
        @Body body: AuthorizedUserRequest,
    ): AuthorizedUserEnvelope

    @DELETE("authorized-users/{id}")
    suspend fun deleteAuthorizedUser(@Path("id") id: Int): MutationResponse

    // Swift sends PATCH with no body; OkHttp requires one, and Laravel
    // tolerates an empty JSON object.
    @PATCH("authorized-users/{id}/activate")
    suspend fun activateAuthorizedUser(@Path("id") id: Int, @Body body: EmptyRequest): MutationResponse

    @PATCH("authorized-users/{id}/deactivate")
    suspend fun deactivateAuthorizedUser(@Path("id") id: Int, @Body body: EmptyRequest): MutationResponse

    // ── Refer a friend ──
    @GET("refer-friend")
    suspend fun referredFriends(@Query("limit") limit: Int): Paginated<ReferredFriend>

    @POST("refer-friend")
    suspend fun referFriend(@Body body: ReferFriendRequest): MutationResponse

    @GET("user/profile")
    suspend fun profile(): DataEnvelope<AirdropUser>

    // ── Content / info ──
    @GET("promotional-banners")
    suspend fun promotionalBanners(): Paginated<PromotionalBanner>

    @GET("shipping-rates")
    suspend fun shippingRates(): DataEnvelope<ShippingRates>

    @GET("faqs")
    suspend fun faqs(): Paginated<FaqItem>

    // CMS bodies may be raw HTML (not JSON) — take the raw body and decode
    // manually, mirroring Swift cmsContent().
    @GET("content/terms-conditions")
    suspend fun termsContent(): ResponseBody

    @GET("content/privacy-policy")
    suspend fun privacyContent(): ResponseBody

    // ── Account deletion ──
    // Swift verifyAccountCredentials re-runs /login without persisting the
    // returned token (AuthTokenStore is only written by the login screen).
    @POST("login")
    suspend fun verifyLogin(@Body body: LoginRequest): LoginResponse

    @POST("user/deactivate-account")
    suspend fun deactivateAccount(@Body body: DeactivateAccountRequest): MutationResponse
}

/** Thin Result-returning wrapper used by the more2 ViewModels. */
class More2Repository(
    private val api: More2Api = ApiClient.retrofit.create(More2Api::class.java),
) {

    // runCatching swallows CancellationException, turning coroutine cancellation
    // into a spurious error dialog; rethrow it and wrap only real failures.
    private suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
        try { Result.success(block()) }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Throwable) { Result.failure(e) }

    suspend fun authorizedUsers(): Result<AuthorizedUsers> =
        apiCall { api.authorizedUsers().users }

    suspend fun authorizedUser(id: Int) =
        apiCall { api.authorizedUser(id).user ?: error("User not found") }

    suspend fun addAuthorizedUser(request: AuthorizedUserRequest) =
        apiCall { api.addAuthorizedUser(request) }

    suspend fun updateAuthorizedUser(id: Int, request: AuthorizedUserRequest) =
        apiCall { api.updateAuthorizedUser(id, request) }

    suspend fun deleteAuthorizedUser(id: Int) =
        apiCall { api.deleteAuthorizedUser(id) }

    suspend fun activateAuthorizedUser(id: Int) =
        apiCall { api.activateAuthorizedUser(id, EmptyRequest()) }

    suspend fun deactivateAuthorizedUser(id: Int) =
        apiCall { api.deactivateAuthorizedUser(id, EmptyRequest()) }

    suspend fun referredFriends(limit: Int = 20): Result<List<ReferredFriend>> =
        apiCall { api.referredFriends(limit).items }

    suspend fun referFriend(request: ReferFriendRequest) =
        apiCall { api.referFriend(request) }

    suspend fun profile(): Result<AirdropUser> =
        apiCall { api.profile().data ?: error("No profile returned") }

    suspend fun promotionalBanners(activeOnly: Boolean = true): Result<List<PromotionalBanner>> =
        apiCall {
            val banners = api.promotionalBanners().items
            if (activeOnly) banners.filter { it.active == true } else banners
        }

    suspend fun shippingRates(): Result<ShippingRates> =
        apiCall { api.shippingRates().data ?: ShippingRates() }

    suspend fun faqs(): Result<List<FaqItem>> =
        apiCall { api.faqs().items.filter { it.question.isNotBlank() } }

    suspend fun termsContent(): Result<String> =
        apiCall { parseCmsBody(api.termsContent().string()) }

    suspend fun privacyContent(): Result<String> =
        apiCall { parseCmsBody(api.privacyContent().string()) }

    /**
     * Swift verifyAccountCredentials: validates locally, re-runs /login and
     * returns true when a token comes back. The token is NOT persisted.
     */
    suspend fun verifyCredentials(email: String, password: String): Result<Boolean> {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please enter your email address."))
        }
        if (password.length < 6) {
            return Result.failure(IllegalArgumentException("Password must be at least 6 characters long."))
        }
        return apiCall { api.verifyLogin(LoginRequest(trimmed, password)).token != null }
    }

    /**
     * RN deactivateAccount.ts sends password == password_confirmation; the
     * selected reason is not part of the Laravel payload (kept client-side).
     */
    suspend fun deactivateAccount(password: String) =
        apiCall {
            api.deactivateAccount(
                DeactivateAccountRequest(password = password, passwordConfirmation = password),
            )
        }

    /** {data:{content|html|...}}, {"..."} JSON string, or raw HTML body. */
    private fun parseCmsBody(raw: String): String {
        val trimmed = raw.trim()
        val decoded = runCatching {
            AirdropJson.decodeFromString(CmsContentResponse.serializer(), trimmed)
        }.getOrNull()?.content?.trim()
        if (!decoded.isNullOrEmpty()) return decoded
        if (trimmed.isEmpty()) error("No content returned.")
        return trimmed
    }
}
