package com.qolve.fluyo.data.repository

import com.qolve.fluyo.domain.model.EmailGrant
import com.qolve.fluyo.domain.repository.GmailAuthorizationException
import com.qolve.fluyo.domain.repository.GmailAuthorizationFailure
import com.qolve.fluyo.domain.repository.EmailGrantRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseEmailGrantRepository @Inject constructor(
    private val client: SupabaseClient,
) : EmailGrantRepository {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GrantRow(
        val email: String,
        @SerialName("watch_expiration") val watchExpiration: String? = null,
        @SerialName("last_error") val lastError: String? = null,
    )

    @Serializable
    private data class DisconnectResponse(val disconnected: Int = 0)

    override suspend fun linkedGrant(): Result<EmailGrant?> = runCatching {
        client.postgrest.from("email_grants")
            .select(Columns.list("email", "watch_expiration", "last_error")) {
                limit(1)
            }
            .decodeList<GrantRow>()
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun createAuthorizationUrl(redirectUri: String): Result<String> = runCatching {
        val response = client.functions.invoke(
            function = GmailConnectContract.FUNCTION_NAME,
            body = GmailConnectContract.init(redirectUri),
            headers = Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            },
        )
        check(response.status.value in 200..299) { "gmail-connect start failed" }
        json.decodeFromString<GmailConnectContract.InitResponse>(response.bodyAsText())
            .authorizationUrl
            .takeIf { it.isNotBlank() }
            ?: error("gmail-connect returned an invalid authorization URL")
    }

    override suspend fun completeAuthorization(
        authorizationCode: String,
        state: String,
    ): Result<Unit> = try {
        val response = client.functions.invoke(
            function = GmailConnectContract.FUNCTION_NAME,
            body = GmailConnectContract.complete(authorizationCode, state),
            headers = Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            },
        )
        check(response.status.value in 200..299) { "gmail-connect completion failed" }
        val payload = json.decodeFromString<GmailConnectContract.CompleteResponse>(
            response.bodyAsText(),
        )
        if (payload.status != "success") {
            Result.failure(GmailAuthorizationException(GmailAuthorizationFailure.UNKNOWN))
        } else {
            Result.success(Unit)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error.toSafeCompletionException())
    }

    override suspend fun disconnect(): Result<Int> = try {
        // The Functions client still supplies the current Supabase Authorization header;
        // only the HTTP verb is overridden here. No OAuth token ever reaches Android.
        val response = client.functions.invoke(GmailConnectContract.FUNCTION_NAME) {
            method = HttpMethod.Delete
        }
        check(response.status.value in 200..299) { "gmail-connect disconnect failed" }
        Result.success(
            json.decodeFromString<DisconnectResponse>(response.bodyAsText()).disconnected,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun GrantRow.toDomain(): EmailGrant = EmailGrant(
        email = email.trim(),
        watchExpiration = watchExpiration?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
        },
        lastError = lastError?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
    )

    private fun Throwable.toSafeCompletionException(): GmailAuthorizationException {
        val restError = this as? RestException
        val serverCode = restError
            ?.error
            ?.takeIf { it.length <= MAX_ERROR_BODY_LENGTH }
            ?.let { body ->
                runCatching {
                    json.decodeFromString<GmailConnectContract.ErrorResponse>(body).error
                }.getOrNull()
            }
        val mapped = GmailAuthorizationFailure.fromServerCode(serverCode)
        val safeFailure = if (mapped == GmailAuthorizationFailure.UNKNOWN &&
            restError?.statusCode == 401
        ) {
            GmailAuthorizationFailure.UNAUTHORIZED
        } else {
            mapped
        }
        return GmailAuthorizationException(safeFailure)
    }

    private companion object {
        const val MAX_ERROR_BODY_LENGTH = 4_096
    }
}
