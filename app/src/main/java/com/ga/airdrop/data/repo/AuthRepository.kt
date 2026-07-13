package com.ga.airdrop.data.repo

import com.ga.airdrop.core.auth.AuthTokenStore
import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.ForgotPasswordRequest
import com.ga.airdrop.data.model.LoginRequest
import com.ga.airdrop.data.model.LoginResponse
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.ReactivateAccountRequest
import com.ga.airdrop.data.model.ResetPasswordRequest
import com.ga.airdrop.data.model.SignUpRequest

class AuthRepository(private val service: AirdropApiService) {

    // Callers persist the token themselves (mirrors Swift, where login()
    // does not auto-save and LoginVC calls setAuthToken explicitly).
    suspend fun login(email: String, password: String): Result<LoginResponse> =
        apiResult { service.login(LoginRequest(email = email, password = password)) }

    suspend fun signUp(request: SignUpRequest): Result<LoginResponse> = apiResult {
        val response = service.register(request)
        // Registration may return a real bearer, but Swift keeps the user
        // signed out until email verification and an explicit login.
        AuthTokenStore.clear()
        response
    }

    suspend fun forgotPassword(email: String): Result<MutationResponse> =
        apiResult { service.forgotPassword(ForgotPasswordRequest(email)) }

    suspend fun resetPassword(
        email: String,
        token: String,
        password: String,
        passwordConfirmation: String,
    ): Result<MutationResponse> = apiResult {
        service.resetPassword(
            ResetPasswordRequest(
                token = token,
                email = email,
                password = password,
                passwordConfirmation = passwordConfirmation,
            ),
        )
    }

    suspend fun logout(): Result<MutationResponse> =
        apiResult { service.logout(EmptyRequest()) }

    suspend fun reactivateAccount(
        email: String,
        password: String,
        passwordConfirmation: String,
    ): Result<LoginResponse> = apiResult {
        val response = service.reactivateAccount(
            ReactivateAccountRequest(
                email = email,
                password = password,
                passwordConfirmation = passwordConfirmation,
            ),
        )
        response.token?.let(AuthTokenStore::save)
        response
    }
}
