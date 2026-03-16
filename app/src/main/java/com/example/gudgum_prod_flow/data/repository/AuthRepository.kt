package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.AppDatabase
import com.example.gudgum_prod_flow.data.local.entity.UserEntity
import com.example.gudgum_prod_flow.data.network.ConnectivityObserver
import com.example.gudgum_prod_flow.data.remote.api.SupabaseApiClient
import com.example.gudgum_prod_flow.data.security.TokenManager
import com.example.gudgum_prod_flow.domain.model.AuthResult
import com.example.gudgum_prod_flow.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val database: AppDatabase,
    private val tokenManager: TokenManager,
    private val connectivityObserver: ConnectivityObserver
) {
    private val api = SupabaseApiClient.api
    private val mobileModules = setOf("inwarding", "production", "packing", "dispatch")

    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn

    suspend fun login(phone: String): AuthResult {
        return if (connectivityObserver.isOnline()) {
            loginOnline(phone)
        } else {
            loginOffline(phone)
        }
    }

    private suspend fun loginOnline(phone: String): AuthResult {
        return try {
            val response = api.getGgUserByPhone(mobileNumber = "eq.$phone")
            if (response.isSuccessful) {
                val users = response.body() ?: emptyList()
                if (users.isEmpty()) {
                    return AuthResult.Error("Phone number not registered. Contact admin.")
                }
                val ggUser = users.first()
                if (!ggUser.active) {
                    return AuthResult.Error("Your account is inactive. Contact admin.")
                }

                val allowedModules = ggUser.modules
                    .map { it.trim().lowercase() }
                    .filter { mobileModules.contains(it) }

                // Save session marker so isLoggedIn returns true (30-day TTL)
                tokenManager.saveTokens(
                    accessToken = ggUser.id,
                    refreshToken = "",
                    expiresInSeconds = 60L * 60L * 24L * 30L
                )

                // Cache user in Room for offline access
                database.userDao().insertUser(
                    UserEntity(
                        userId = ggUser.id,
                        tenantId = "",
                        phone = ggUser.mobileNumber,
                        name = ggUser.name,
                        role = ggUser.role,
                        status = "active",
                        factoryIds = "[]",
                        permissions = Json.encodeToString(allowedModules),
                        lastSyncTimestamp = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis()
                    )
                )

                AuthResult.Success(
                    user = User(
                        userId = ggUser.id,
                        tenantId = "",
                        phone = ggUser.mobileNumber,
                        name = ggUser.name,
                        role = ggUser.role,
                        factoryIds = emptyList(),
                        allowedModules = allowedModules,
                    )
                )
            } else {
                AuthResult.Error("Login failed (${response.code()}). Try again.")
            }
        } catch (e: Exception) {
            loginOffline(phone)
        }
    }

    private suspend fun loginOffline(phone: String): AuthResult {
        val userEntity = database.userDao().getUserByPhone(phone)
            ?: return AuthResult.Error("User not found. Online authentication required for first login.")

        if (userEntity.status == "locked" || userEntity.status == "suspended") {
            return AuthResult.Error("Account is ${userEntity.status}. Contact your supervisor.")
        }

        val allowedModules = try {
            Json.decodeFromString<List<String>>(userEntity.permissions)
                .filter { mobileModules.contains(it.trim().lowercase()) }
        } catch (_: Exception) {
            emptyList()
        }

        return AuthResult.Success(
            user = User(
                userId = userEntity.userId,
                tenantId = userEntity.tenantId,
                phone = userEntity.phone,
                name = userEntity.name,
                role = userEntity.role,
                factoryIds = emptyList(),
                allowedModules = allowedModules,
            ),
            offlineMode = true
        )
    }

    suspend fun logout() {
        tokenManager.clearTokens()
    }
}
