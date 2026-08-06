package com.qolve.fluyo.data.repository

import com.qolve.fluyo.domain.repository.EmailGrantRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseEmailGrantRepository @Inject constructor(
    private val client: SupabaseClient,
) : EmailGrantRepository {

    @Serializable
    private data class GrantRow(val email: String)

    override suspend fun linkedEmail(): String? = runCatching {
        // RLS on email_grants restricts to the current user's own rows, so a single
        // select returns at most one (UNIQUE email per user).
        client.postgrest.from("email_grants")
            .select {
                limit(1)
            }
            .decodeList<GrantRow>()
            .firstOrNull()
            ?.email
    }.getOrNull()
}
