package com.qolve.fluyo.di

import android.content.Context
import com.qolve.fluyo.BuildConfig
import com.qolve.fluyo.data.security.EncryptedSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideEncryptedSessionManager(
        @ApplicationContext context: Context,
    ): EncryptedSessionManager {
        val legacyKey = "sb-${BuildConfig.SUPABASE_URL.removeSuffix("/")
            .replace('/', '-')
            .replace('.', '-')}-session"
        return EncryptedSessionManager(
            context = context,
            legacyManager = SettingsSessionManager(key = legacyKey),
        )
    }

    @Provides
    @Singleton
    fun provideSupabaseClient(
        encryptedSessionManager: EncryptedSessionManager,
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            sessionManager = encryptedSessionManager
            // Never place bearer tokens in a custom-scheme callback. PKCE binds the
            // short-lived authorization code to the verifier held by this app, so an
            // app that also registers `fluyo://` cannot exchange an intercepted code.
            flowType = FlowType.PKCE
            // Email confirmations and OTP links return here. This exact URL must also be
            // allow-listed in Supabase Auth → Redirect URLs.
            scheme = "fluyo"
            host = "auth-callback"
        }
        install(Postgrest)
        install(Storage)
        install(ComposeAuth) {
            // Only initialize Google login if clientId is provided
            // This is initialized later in LoginScreen.rememberSignInWithGoogle()
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                try {
                    googleNativeLogin(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
                } catch (_: Exception) {
                    // If Google Play Services is not available, log and continue
                    // The error will be caught when user tries to sign in
                    if (BuildConfig.DEBUG) {
                        android.util.Log.w("SupabaseModule", "Google login is unavailable")
                    }
                }
            }
        }
    }
}
