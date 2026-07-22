package com.qolve.fluyo.di

import com.qolve.fluyo.data.repository.SupabaseAuthRepository
import com.qolve.fluyo.data.repository.SupabaseBadgeRepository
import com.qolve.fluyo.data.repository.SupabaseBudgetExtraRepository
import com.qolve.fluyo.data.repository.SupabaseCategoryRepository
import com.qolve.fluyo.data.repository.SupabaseExpenseRepository
import com.qolve.fluyo.data.repository.SupabaseGoalRepository
import com.qolve.fluyo.data.repository.SupabaseWhatsAppLinkRepository
import com.qolve.fluyo.data.local.NudgePrefs
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.BudgetExtraRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.GoalRepository
import com.qolve.fluyo.domain.repository.WhatsAppLinkRepository
import com.qolve.fluyo.domain.repository.NudgeHistoryRepository
import com.qolve.fluyo.domain.repository.BadgeEventPublisher
import com.qolve.fluyo.domain.repository.BadgeNotificationGateway
import com.qolve.fluyo.notifications.BadgeNotifier
import com.qolve.fluyo.presentation.events.AppEvents
import com.qolve.fluyo.data.SessionEpoch
import com.qolve.fluyo.domain.repository.SessionBoundary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: SupabaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: SupabaseCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: SupabaseExpenseRepository): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: SupabaseGoalRepository): GoalRepository

    @Binds
    @Singleton
    abstract fun bindBadgeRepository(impl: SupabaseBadgeRepository): BadgeRepository

    @Binds
    @Singleton
    abstract fun bindBudgetExtraRepository(impl: SupabaseBudgetExtraRepository): BudgetExtraRepository

    @Binds
    @Singleton
    abstract fun bindNudgeHistoryRepository(impl: NudgePrefs): NudgeHistoryRepository

    @Binds
    @Singleton
    abstract fun bindSessionBoundary(impl: SessionEpoch): SessionBoundary

    @Binds
    @Singleton
    abstract fun bindBadgeEventPublisher(impl: AppEvents): BadgeEventPublisher

    @Binds
    @Singleton
    abstract fun bindBadgeNotificationGateway(impl: BadgeNotifier): BadgeNotificationGateway

    @Binds
    @Singleton
    abstract fun bindWhatsAppLinkRepository(
        impl: SupabaseWhatsAppLinkRepository,
    ): WhatsAppLinkRepository
}
