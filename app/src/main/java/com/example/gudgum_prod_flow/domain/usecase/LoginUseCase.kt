package com.example.gudgum_prod_flow.domain.usecase

import com.example.gudgum_prod_flow.data.repository.AuthRepository
import com.example.gudgum_prod_flow.domain.model.AuthResult
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String, pin: String): AuthResult {
        if (phone.isBlank()) {
            return AuthResult.Error("Phone is required.")
        }

        return authRepository.login(phone, pin)
    }
}
