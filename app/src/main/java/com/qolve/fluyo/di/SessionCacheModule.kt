package com.qolve.fluyo.di

import com.qolve.fluyo.data.SessionScopedCache
import com.qolve.fluyo.data.local.OnboardingPrefs
import com.qolve.fluyo.data.repository.SupabaseBadgeRepository
import com.qolve.fluyo.data.repository.SupabaseCategoryRepository
import com.qolve.fluyo.data.repository.SupabaseExpenseRepository
import com.qolve.fluyo.data.repository.SupabaseGoalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Registers every per-user cache holder into a single `Set<SessionScopedCache>`.
 * [com.qolve.fluyo.data.repository.SupabaseAuthRepository.signOut] iterates the set and
 * resets each one before tearing down the Supabase session — closing the loophole where
 * user A's data could leak into user B's view of the app on the same device.
 *
 * **Why @Binds + @IntoSet instead of `@Provides` returning a Set.** Each binding here
 * declares an existing `@Singleton` repository as *also* implementing `SessionScopedCache`.
 * Hilt collects them all without any of them being instantiated more than once — the same
 * instance is shared between (e.g.) the `ExpenseRepository` interface, the
 * `SupabaseExpenseRepository` concrete type, and this cache set.
 *
 * **Adding a new cache holder.** Implement `SessionScopedCache` on the class, then add a
 * `@Binds @IntoSet` entry here. That's the only place sign-out needs to know about it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionCacheModule {

    @Binds
    @IntoSet
    abstract fun bindOnboardingPrefs(impl: OnboardingPrefs): SessionScopedCache

    @Binds
    @IntoSet
    abstract fun bindExpenseCache(impl: SupabaseExpenseRepository): SessionScopedCache

    @Binds
    @IntoSet
    abstract fun bindCategoryCache(impl: SupabaseCategoryRepository): SessionScopedCache

    @Binds
    @IntoSet
    abstract fun bindGoalCache(impl: SupabaseGoalRepository): SessionScopedCache

    @Binds
    @IntoSet
    abstract fun bindBadgeCache(impl: SupabaseBadgeRepository): SessionScopedCache
}
