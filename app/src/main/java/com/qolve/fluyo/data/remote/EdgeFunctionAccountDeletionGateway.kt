package com.qolve.fluyo.data.remote

import com.qolve.fluyo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Calls the privileged account-deletion Edge Function with the current user's JWT. */
@Singleton
class EdgeFunctionAccountDeletionGateway @Inject constructor() {

    suspend fun deleteAccount(accessToken: String) = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Missing authenticated session" }
        require(BuildConfig.SUPABASE_URL.isNotBlank()) { "Supabase URL is not configured" }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) { "Supabase key is not configured" }

        val endpoint = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/delete-account"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Content-Type", "application/json")
            setFixedLengthStreamingMode(2)
        }

        try {
            connection.outputStream.use { it.write("{}".encodeToByteArray()) }
            val status = connection.responseCode
            if (status !in 200..299) {
                // Do not surface a service-role/backend error body to the UI. The HTTP code
                // is enough for support correlation and avoids accidentally leaking details.
                error("Account deletion failed (HTTP $status)")
            }
        } finally {
            connection.disconnect()
        }
    }
}
