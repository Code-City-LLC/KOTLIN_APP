package com.ga.airdrop.feature.more

import com.ga.airdrop.core.auth.AuthTokenStore
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal class RecordingMoreProfileRepository(
    var user: MoreUser = MoreUser(),
) : MoreProfileRepository {

    val currentUserCalls = AtomicInteger()
    val updateProfileCalls = AtomicInteger()
    val profileImageCalls = AtomicInteger()
    val lastProfileUpdate = AtomicReference<Map<String, String?>?>()
    var updateResult: Result<String?> = Result.success("OK")

    override suspend fun currentUser(): Result<MoreUser> {
        currentUserCalls.incrementAndGet()
        return Result.success(user)
    }

    override suspend fun updateProfile(
        fields: Map<String, String?>,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<String?> {
        updateProfileCalls.incrementAndGet()
        lastProfileUpdate.set(fields)
        return updateResult
    }

    override suspend fun profileImage(): Result<ProfileAsset> {
        profileImageCalls.incrementAndGet()
        return Result.success(ProfileAsset(url = null, path = null))
    }

    override suspend fun uploadProfileImage(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<ProfileAsset> = Result.success(ProfileAsset(url = null, path = null))

    override suspend fun deleteProfileImage(
        expectedSession: AuthTokenStore.RequestProvenance,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun fetchImage(url: String): Result<ByteArray> =
        Result.failure(IOException("Unexpected image fetch in payload test"))
}
