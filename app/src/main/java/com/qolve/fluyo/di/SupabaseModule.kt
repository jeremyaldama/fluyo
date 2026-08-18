package com.qolve.fluyo.di

import com.qolve.fluyo.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.compose.auth.ComposeAuth
import io.github.jan.supabase.compose.auth.googleNativeLogin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Functions)
        install(Storage)
        install(ComposeAuth) {
            // Only initialize Google login if clientId is provided
            // This is initialized later in LoginScreen.rememberSignInWithGoogle()
            if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                try {
                    googleNativeLogin(serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)
                } catch (e: Exception) {
                    // If Google Play Services is not available, log and continue
                    // The error will be caught when user tries to sign in
                    android.util.Log.w("SupabaseModule", "Google login not available: ${e.message}")
                }
            }
        }
    }
}
