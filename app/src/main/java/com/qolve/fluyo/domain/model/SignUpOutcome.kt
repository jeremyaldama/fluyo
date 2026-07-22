package com.qolve.fluyo.domain.model

/**
 * Result of creating an email/password account.
 *
 * Supabase may either create a session immediately (email confirmation disabled) or
 * require the user to follow the confirmation link before a session exists. Keeping
 * those outcomes distinct prevents callers from attempting authenticated provisioning
 * while there is no authenticated session yet.
 */
sealed interface SignUpOutcome {
    data object Authenticated : SignUpOutcome
    data class ConfirmationRequired(val email: String) : SignUpOutcome
}
