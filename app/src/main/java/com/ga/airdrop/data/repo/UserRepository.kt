package com.ga.airdrop.data.repo

import com.ga.airdrop.data.api.AirdropApiService
import com.ga.airdrop.data.api.UploadFile
import com.ga.airdrop.data.api.textPart
import com.ga.airdrop.data.api.toPart
import com.ga.airdrop.data.model.AirdropUser
import com.ga.airdrop.data.model.AuthorizedUser
import com.ga.airdrop.data.model.AuthorizedUserRequest
import com.ga.airdrop.data.model.AuthorizedUsers
import com.ga.airdrop.data.model.DeactivateAccountRequest
import com.ga.airdrop.data.model.EmptyRequest
import com.ga.airdrop.data.model.MutationResponse
import com.ga.airdrop.data.model.ProfileAssetResponse
import com.ga.airdrop.data.model.ProfileMutationResponse
import com.ga.airdrop.data.model.ProfileUpdateRequest
import com.ga.airdrop.data.model.ReferFriendRequest
import com.ga.airdrop.data.model.ReferredFriend
import com.ga.airdrop.data.model.UserDocumentType
import com.ga.airdrop.data.model.UserDocuments

class UserRepository(private val service: AirdropApiService) {

    suspend fun currentUser(): Result<AirdropUser> = apiResult {
        service.currentUser().user ?: error("User not found")
    }

    suspend fun updateProfile(request: ProfileUpdateRequest): Result<ProfileMutationResponse> =
        apiResult { service.updateProfile(request) }

    suspend fun userDocuments(): Result<UserDocuments> =
        apiResult { service.userDocuments() }

    suspend fun uploadUserDocument(
        type: UserDocumentType,
        file: UploadFile,
    ): Result<ProfileAssetResponse> = apiResult {
        service.uploadUserDocument(
            documentType = textPart(type.wireName),
            file = file.toPart(type.wireName),
        )
    }

    suspend fun deleteUserDocument(identifier: String): Result<MutationResponse> =
        apiResult { service.deleteUserDocument(identifier) }

    suspend fun profileImage(): Result<ProfileAssetResponse> =
        apiResult { service.profileImage() }

    suspend fun uploadProfileImage(file: UploadFile): Result<ProfileAssetResponse> =
        apiResult { service.uploadProfileImage(file.toPart("image")) }

    suspend fun deleteProfileImage(): Result<MutationResponse> =
        apiResult { service.deleteProfileImage() }

    // Swift sends password as its own confirmation; the backend ignores any
    // deactivation reason.
    suspend fun deactivateAccount(password: String): Result<MutationResponse> =
        deactivateAccount(password, password)

    suspend fun deactivateAccount(
        password: String,
        passwordConfirmation: String,
    ): Result<MutationResponse> = apiResult {
        service.deactivateAccount(
            DeactivateAccountRequest(
                password = password,
                passwordConfirmation = passwordConfirmation,
            ),
        )
    }

    // ── Authorized users ──

    suspend fun authorizedUsers(): Result<AuthorizedUsers> =
        apiResult { service.authorizedUsers().users }

    suspend fun authorizedUser(id: Int): Result<AuthorizedUser?> =
        apiResult { service.authorizedUser(id).user }

    suspend fun addAuthorizedUser(request: AuthorizedUserRequest): Result<AuthorizedUser?> =
        apiResult { service.addAuthorizedUser(request).user }

    suspend fun updateAuthorizedUser(
        id: Int,
        request: AuthorizedUserRequest,
    ): Result<AuthorizedUser?> =
        apiResult { service.updateAuthorizedUser(id, request).user }

    suspend fun deleteAuthorizedUser(id: Int): Result<MutationResponse> =
        apiResult { service.deleteAuthorizedUser(id) }

    suspend fun activateAuthorizedUser(id: Int): Result<MutationResponse> =
        apiResult { service.activateAuthorizedUser(id, EmptyRequest()) }

    suspend fun deactivateAuthorizedUser(id: Int): Result<MutationResponse> =
        apiResult { service.deactivateAuthorizedUser(id, EmptyRequest()) }

    // ── Referrals ──

    suspend fun referFriend(
        firstName: String,
        lastName: String,
        email: String,
        description: String?,
    ): Result<MutationResponse> = apiResult {
        service.referFriend(
            ReferFriendRequest(
                friendFirstName = firstName,
                friendLastName = lastName,
                friendEmail = email,
                description = description,
            ),
        )
    }

    suspend fun referredFriends(userId: Int? = null, limit: Int = 20): Result<List<ReferredFriend>> =
        apiResult { service.referredFriends(limit = limit, userId = userId).items }
}
