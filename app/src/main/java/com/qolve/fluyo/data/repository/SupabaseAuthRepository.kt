package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionIdentityCoordinator
import com.qolve.fluyo.data.requireCurrent
import com.qolve.fluyo.data.dto.NotificationSettingsUpdateDto
import com.qolve.fluyo.data.dto.UserDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.data.remote.EdgeFunctionAccountDeletionGateway
import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.SignUpOutcome
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.model.MoneyAmount
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.SessionBoundary
import com.qolve.fluyo.domain.suspendRunCatching
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient,
    private val sessionIdentity: SessionIdentityCoordinator,
    private val deletionGateway: EdgeFunctionAccountDeletionGateway,
    private val sessionBoundary: SessionBoundary,
) : AuthRepository {

    private data class CachedUserId(val authId: String, val publicUserId: String)

    // The Auth id travels with the cached public id, so a direct A→B session replacement
    // can never reuse A's database id even before the root coordinator sees the event.
    private val cachedUserId = MutableStateFlow<CachedUserId?>(null)

    override val authState: Flow<AuthState> = client.auth.sessionStatus
        .onEach { status ->
            val authId = (status as? SessionStatus.Authenticated)?.session?.user?.id
            if (cachedUserId.value?.authId != authId) cachedUserId.value = null
        }
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.id
                    ?.takeIf { it.isNotBlank() }
                    ?.let(AuthState::SignedIn)
                    ?: AuthState.SignedOut
                is SessionStatus.NotAuthenticated -> AuthState.SignedOut
                is SessionStatus.Initializing -> AuthState.Unknown
                is SessionStatus.RefreshFailure -> AuthState.SignedOut
            }
        }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> = suspendRunCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?,
    ): Result<SignUpOutcome> = suspendRunCatching {
        val normalizedEmail = email.trim()
        val pendingConfirmation = client.auth.signUpWith(Email) {
            this.email = normalizedEmail
            this.password = password
            if (!displayName.isNullOrBlank()) {
                data = buildJsonObject {
                    put("full_name", displayName.trim())
                    // Mirror what Google sign-in fills so ensureUserRow's name extraction works for both providers.
                    put("name", displayName.trim())
                }
            }
        }
        if (pendingConfirmation == null) {
            SignUpOutcome.Authenticated
        } else {
            SignUpOutcome.ConfirmationRequired(normalizedEmail)
        }
    }

    override suspend fun resendSignUpConfirmation(email: String): Result<Unit> = suspendRunCatching {
        val normalizedEmail = email.trim()
        require(normalizedEmail.isNotEmpty()) { "Email is required" }
        client.auth.resendEmail(OtpType.Email.SIGNUP, normalizedEmail)
    }

    override suspend fun signOut(): Result<Unit> = suspendRunCatching {
        // Clear local user state before the network call; sign-out remains fail-closed even
        // when the device is offline and Supabase cannot revoke the remote refresh token.
        withContext(NonCancellable) {
            var firstFailure: Exception? = null
            suspend fun attempt(block: suspend () -> Unit) {
                try {
                    block()
                } catch (failure: Exception) {
                    if (firstFailure == null) firstFailure = failure else firstFailure?.addSuppressed(failure)
                }
            }

            attempt { sessionIdentity.transitionTo(null) }
            cachedUserId.value = null
            attempt { withTimeout(15_000) { client.auth.signOut() } }
            attempt { client.auth.clearSession() }
            firstFailure?.let { throw it }
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = suspendRunCatching {
        val session = client.auth.currentSessionOrNull() ?: error("No authenticated user")
        val authId = session.user?.id?.takeIf { it.isNotBlank() }
            ?: error("Authenticated identity is missing")
        // The server route verifies this JWT, removes external/storage artifacts and uses
        // service-role privileges to delete auth.users. A 2xx response is the only success.
        deletionGateway.deleteAccount(session.accessToken)

        // The remote account is already gone after the 2xx above. From this point every
        // local cleanup step is attempted even when an earlier cache/DataStore operation
        // fails, so a dead session can never remain usable on the device.
        withContext(NonCancellable) {
            var firstFailure: Exception? = null
            suspend fun attempt(block: suspend () -> Unit) {
                try {
                    block()
                } catch (failure: Exception) {
                    if (firstFailure == null) firstFailure = failure else firstFailure?.addSuppressed(failure)
                }
            }
            attempt { sessionIdentity.transitionTo(null) }
            attempt { sessionIdentity.forgetUser(authId) }
            cachedUserId.value = null
            attempt { client.auth.clearSession() }
            firstFailure?.let { throw it }
        }
    }

    override suspend fun currentUserId(): String? {
        val sessionEpoch = sessionBoundary.snapshot()
        if (!sessionBoundary.isCurrent(sessionEpoch)) return null
        val authUser = client.auth.currentUserOrNull() ?: return null
        cachedUserId.value
            ?.takeIf { it.authId == authUser.id }
            ?.let { return it.publicUserId }
        val row = client.postgrest.from("users")
            .select { filter { eq("auth_id", authUser.id) } }
            .decodeSingleOrNull<UserDto>() ?: return null
        if (!sessionBoundary.isCurrent(sessionEpoch)) return null
        cachedUserId.value = CachedUserId(authUser.id, row.id)
        return row.id
    }

    override suspend fun hasMonetaryActivity(): Result<Boolean> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val userId = currentUserId() ?: error("No authenticated user")
        val hasActivity = listOf("expenses", "goals", "budget_extras").any { table ->
            client.postgrest.from(table)
                .select(io.github.jan.supabase.postgrest.query.Columns.list("id")) {
                    filter { eq("user_id", userId) }
                    limit(1)
                }
                .decodeList<IdProjection>()
                .isNotEmpty()
        }
        sessionBoundary.requireCurrent(sessionEpoch)
        hasActivity
    }

    override suspend fun ensureUserRow(): Result<User> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val authUser = client.auth.currentUserOrNull()
            ?: error("No authenticated user")

        // One atomic SECURITY DEFINER operation owns profile provisioning, metadata
        // normalization and the deletion tombstone check. This removes the SELECT→INSERT
        // race and remains compatible after direct users INSERT is revoked by contract 0008.
        val inserted = client.postgrest.rpc("ensure_user_profile")
            .decodeSingle<UserDto>()
        sessionBoundary.requireCurrent(sessionEpoch)
        cachedUserId.value = CachedUserId(authUser.id, inserted.id)
        inserted.toDomain()
    }

    override suspend fun currentUser(): Result<User?> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val authUser = client.auth.currentUserOrNull() ?: return@suspendRunCatching null
        val row = client.postgrest.from("users")
            .select { filter { eq("auth_id", authUser.id) } }
            .decodeSingleOrNull<UserDto>()
            ?.toDomain()
        sessionBoundary.requireCurrent(sessionEpoch)
        // Merge Google avatar at read time — not persisted to our table. Supabase populates
        // `avatar_url` (and Google's `picture` claim) on the auth user metadata after a
        // successful Google sign-in.
        row?.copy(avatarUrl = extractAvatarUrl(authUser.userMetadata))
    }

    private fun extractAvatarUrl(meta: kotlinx.serialization.json.JsonObject?): String? {
        if (meta == null) return null
        return (meta["avatar_url"] ?: meta["picture"])
            ?.toString()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    override suspend fun updateProfile(
        monthlyBudget: MoneyAmount?,
        currency: String?,
    ): Result<User> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val authUser = client.auth.currentUserOrNull()
            ?: error("No authenticated user")

        val updated = client.postgrest.from("users")
            .update({
                monthlyBudget?.let { set("monthly_budget", it.toTransportDouble()) }
                currency?.takeIf { it.isNotBlank() }?.let { set("currency", it) }
            }) {
                filter { eq("auth_id", authUser.id) }
                select()
            }
            .decodeSingle<UserDto>()
            .toDomain()
        sessionBoundary.requireCurrent(sessionEpoch)
        updated
    }

    override suspend fun updateNotificationSettings(
        enabled: Boolean?,
        hour: Int?,
        types: Set<NudgeType>?,
    ): Result<User> = suspendRunCatching {
        val sessionEpoch = sessionBoundary.snapshot()
        sessionBoundary.requireCurrent(sessionEpoch)
        val authUser = client.auth.currentUserOrNull() ?: error("No authenticated user")
        val patch = NotificationSettingsUpdateDto(
            notificationEnabled = enabled,
            notificationHour = hour?.coerceIn(0, 23),
            notificationTypes = types?.map { it.wire },
        )
        val updated = client.postgrest.from("users")
            .update(patch) {
                filter { eq("auth_id", authUser.id) }
                select()
            }
            .decodeSingle<UserDto>()
            .toDomain()
        sessionBoundary.requireCurrent(sessionEpoch)
        updated
    }
}

@Serializable
private data class IdProjection(val id: String)
