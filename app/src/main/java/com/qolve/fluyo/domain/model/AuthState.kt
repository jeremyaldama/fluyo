package com.qolve.fluyo.domain.model

sealed interface AuthState {
    data object Unknown : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val authId: String) : AuthState
}
