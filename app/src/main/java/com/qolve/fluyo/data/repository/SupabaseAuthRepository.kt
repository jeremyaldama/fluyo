package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.dto.NotificationSettingsUpdateDto
import com.qolve.fluyo.data.dto.UserDto
import com.qolve.fluyo.data.dto.UserProfileUpdateDto
import com.qolve.fluyo.data.dto.UserUpsertDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.model.NudgeType
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient,
    // Lazy on purpose: every repo in the set depends on AuthRepository (this class), so a
    // direct injection would create a Dagger cycle. `dagger.Lazy` defers resolution until
    // the first signOut() call.
    private val sessionCaches: dagger.Lazy<Set<@JvmSuppressWildcards SessionScopedCache>>,
) : AuthRepository {

    // Cached public.users.id for the signed-in user. Cleared on sign-out.
    private val cachedUserId = MutableStateFlow<String?>(null)

    override val authState: Flow<AuthState> = client.auth.sessionStatus
        .onEach { status ->
            if (status !is SessionStatus.Authenticated) cachedUserId.value = null
        }
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user?.id ?: "")
                is SessionStatus.NotAuthenticated -> AuthState.SignedOut
                is SessionStatus.Initializing -> AuthState.Unknown
                is SessionStatus.RefreshFailure -> AuthState.SignedOut
            }
        }

    override suspend fun signInWithEmail(email: String, password: String): Result<Unit> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String?,
    ): Result<Unit> = runCatching {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            if (!displayName.isNullOrBlank()) {
                data = buildJsonObject {
                    put("full_name", displayName.trim())
                    // Mirror what Google sign-in fills so ensureUserRow's name extraction works for both providers.
                    put("name", displayName.trim())
                }
            }
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        // Clear local caches FIRST so the UI doesn't render the outgoing user's data
        // during the navigation transition. Each cache is responsible for resetting its
        // own in-memory state — see [SessionScopedCache] for the contract.
        sessionCaches.get().forEach { runCatching { it.clearForSignOut() } }
        cachedUserId.value = null
        client.auth.signOut()
    }

    override suspend fun currentUserId(): String? {
        cachedUserId.value?.let { return it }
        val authUser = client.auth.currentUserOrNull() ?: return null
        val row = client.postgrest.from("users")
            .select { filter { eq("auth_id", authUser.id) } }
            .decodeSingleOrNull<UserDto>() ?: return null
        cachedUserId.value = row.id
        return row.id
    }

    override suspend fun ensureUserRow(): Result<User> = runCatching {
        val authUser = client.auth.currentUserOrNull()
            ?: error("No authenticated user")

        val existing = client.postgrest.from("users")
            .select { filter { eq("auth_id", authUser.id) } }
            .decodeSingleOrNull<UserDto>()

        if (existing != null) {
            cachedUserId.value = existing.id
            return@runCatching existing.toDomain()
        }

        val displayName = authUser.userMetadata?.get("full_name")?.toString()?.trim('"')
            ?: authUser.userMetadata?.get("name")?.toString()?.trim('"')

        val insert = UserUpsertDto(
            authId = authUser.id,
            email = authUser.email,
            displayName = displayName,
        )

        val inserted = client.postgrest.from("users")
            .insert(insert) { select() }
            .decodeSingle<UserDto>()
        cachedUserId.value = inserted.id
        inserted.toDomain()
    }

    override suspend fun currentUser(): Result<User?> = runCatching {
        val authUser = client.auth.currentUserOrNull() ?: return@runCatching null
        val row = client.postgrest.from("users")
            .select { filter { eq("auth_id", authUser.id) } }
            .decodeSingleOrNull<UserDto>()
            ?.toDomain()
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
        monthlyBudget: Double?,
        phoneNumber: String?,
    ): Result<User> = runCatching {
        val authUser = client.auth.currentUserOrNull()
            ?: error("No authenticated user")

        val patch = UserProfileUpdateDto(
            monthlyBudget = monthlyBudget,
            phoneNumber = phoneNumber?.takeIf { it.isNotBlank() },
        )

        client.postgrest.from("users")
            .update(patch) {
                filter { eq("auth_id", authUser.id) }
                select()
            }
            .decodeSingle<UserDto>()
            .toDomain()
    }

    override suspend fun updateNotificationSettings(
        enabled: Boolean?,
        hour: Int?,
        types: Set<NudgeType>?,
    ): Result<User> = runCatching {
        val authUser = client.auth.currentUserOrNull() ?: error("No authenticated user")
        val patch = NotificationSettingsUpdateDto(
            notificationEnabled = enabled,
            notificationHour = hour?.coerceIn(0, 23),
            notificationTypes = types?.map { it.wire },
        )
        client.postgrest.from("users")
            .update(patch) {
                filter { eq("auth_id", authUser.id) }
                select()
            }
            .decodeSingle<UserDto>()
            .toDomain()
    }
}
