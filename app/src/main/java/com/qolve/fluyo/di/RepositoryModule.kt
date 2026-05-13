package com.qolve.fluyo.di

import com.qolve.fluyo.data.repository.SupabaseAuthRepository
import com.qolve.fluyo.data.repository.SupabaseBadgeRepository
import com.qolve.fluyo.data.repository.SupabaseCategoryRepository
import com.qolve.fluyo.data.repository.SupabaseExpenseRepository
import com.qolve.fluyo.data.repository.SupabaseGoalRepository
import com.qolve.fluyo.domain.repository.AuthRepository
import com.qolve.fluyo.domain.repository.BadgeRepository
import com.qolve.fluyo.domain.repository.CategoryRepository
import com.qolve.fluyo.domain.repository.ExpenseRepository
import com.qolve.fluyo.domain.repository.GoalRepository
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
}
