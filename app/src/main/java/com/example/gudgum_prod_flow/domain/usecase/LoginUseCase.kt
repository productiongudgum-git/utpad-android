package com.example.gudgum_prod_flow.domain.usecase

import com.example.gudgum_prod_flow.data.repository.AuthRepository
import com.example.gudgum_prod_flow.domain.model.AuthResult
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(phone: String, pin: String): AuthResult {
        // Validate phone format (Indian mobile: 10 digits starting with 6-9)
        if (!phone.matches(Regex("^[6-9]\\d{9}$"))) {
            return AuthResult.Error("Invalid phone number. Must be 10 digits starting with 6-9.")
        }

        // Validate PIN format (4-6 digits, no sequential/repeated patterns)
        if (!pin.matches(Regex("^\\d{4,6}$"))) {
            return AuthResult.Error("PIN must be 4-6 digits.")
        }
        if (isSequentialPattern(pin) || isRepeatedPattern(pin)) {
            return AuthResult.Error("PIN cannot be sequential (1234) or repeated (1111).")
        }

        return authRepository.login(phone, pin)
    }

    private fun isSequentialPattern(pin: String): Boolean {
        if (pin.length < 2) return false
        val ascending = pin.zipWithNext().all { (a, b) -> b - a == 1 }
        val descending = pin.zipWithNext().all { (a, b) -> a - b == 1 }
        return ascending || descending
    }

    private fun isRepeatedPattern(pin: String): Boolean {
        return pin.all { it == pin[0] }
    }
}
