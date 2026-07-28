package com.garfiec.librechat.core.model

sealed interface LoginOutcome {
    data class Success(val user: User) : LoginOutcome
    data class TwoFactorRequired(val tempToken: String) : LoginOutcome
}
