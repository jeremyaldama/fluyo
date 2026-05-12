package com.qolve.fluyo.data.repository

import com.qolve.fluyo.data.dto.UserDto
import com.qolve.fluyo.data.dto.UserProfileUpdateDto
import com.qolve.fluyo.data.dto.UserUpsertDto
import com.qolve.fluyo.data.mapper.toDomain
import com.qolve.fluyo.domain.model.AuthState
import com.qolve.fluyo.domain.model.User
import com.qolve.fluyo.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient,
) : AuthRepository {

    override val authState: Flow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user?.id ?: "")
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            is SessionStatus.Initializing -> AuthState.Unknown
            is SessionStatus.RefreshFailure -> AuthState.SignedOut
        }
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        client.auth.signOut()
    }

    override suspend fun ensureUserRow(): Result<User> = runCatching {
        val authUser = client.auth.currentUserOrNull()
            ?: error("No authenticated user")

        val existing = client.postgrest.from("users")
            .select { filter { eq("auth_id", authUser.id) } }
            .decodeSingleOrNull<UserDto>()

        if (existing != null) return@runCatching existing.toDomain()

        val displayName = authUser.userMetadata?.get("full_name")?.toString()?.trim('"')
            ?: authUser.userMetadata?.get("name")?.toString()?.trim('"')

        val insert = UserUpsertDto(
            authId = authUser.id,
            email = authUser.email,
            displayName = displayName,
        )

        client.postgrest.from("users")
            .insert(insert) { select() }
            .decodeSingle<UserDto>()
            .toDomain()
    }

    override suspend fun currentUser(): Result<User?> = runCatching {
        val authUser = client.auth.currentUserOrNull() ?: return@runCatching null
        client.postgrest.from("users")
            .select { filter { eq("auth_id", authUser.id) } }
            .decodeSingleOrNull<UserDto>()
            ?.toDomain()
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
}
