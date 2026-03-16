package com.example.gudgum_prod_flow.domain.usecase

import com.example.gudgum_prod_flow.data.repository.AuthRepository
import com.example.gudgum_prod_flow.domain.model.AuthResult
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String): AuthResult {
        if (phone.isBlank()) {
            return AuthResult.Error("Phone is required.")
        }
        if (!phone.matches(Regex("^[6-9]\\d{9}$"))) {
            return AuthResult.Error("Enter a valid 10-digit Indian mobile number.")
        }
        return authRepository.login(phone)
    }
}
