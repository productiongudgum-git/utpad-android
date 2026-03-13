package com.example.gudgum_prod_flow.data.repository

import com.example.gudgum_prod_flow.data.local.AppDatabase
import com.example.gudgum_prod_flow.data.local.entity.CachedPermission
import com.example.gudgum_prod_flow.data.local.entity.OfflineAuthEvent
import com.example.gudgum_prod_flow.data.local.entity.UserEntity
import com.example.gudgum_prod_flow.data.network.ConnectivityObserver
import com.example.gudgum_prod_flow.data.remote.api.AuthApiService
import com.example.gudgum_prod_flow.data.remote.dto.PhoneLoginRequest
import com.example.gudgum_prod_flow.data.remote.dto.RefreshTokenRequest
import com.example.gudgum_prod_flow.data.security.SecureCredentialStore
import com.example.gudgum_prod_flow.data.security.TokenManager
import com.example.gudgum_prod_flow.domain.model.AuthResult
import com.example.gudgum_prod_flow.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApiService,
    private val database: AppDatabase,
    private val credentialStore: SecureCredentialStore,
    private val tokenManager: TokenManager,
    private val connectivityObserver: ConnectivityObserver
) {
    private val mobileModules = setOf("inwarding", "production", "packing", "dispatch")

    val isLoggedIn: Flow<Boolean> = tokenManager.isLoggedIn

    suspend fun login(phone: String, pin: String): AuthResult {
        return if (connectivityObserver.isOnline()) {
            loginOnline(phone, pin)
        } else {
            loginOffline(phone, pin)
        }
    }

    private suspend fun loginOnline(phone: String, pin: String): AuthResult {
        return try {
            val response = authApi.loginWithPhone(PhoneLoginRequest(phone, pin))
            if (response.isSuccessful) {
                val body = response.body() ?: return AuthResult.Error("Empty response")
                val user = body.user

                // Save tokens
                tokenManager.saveTokens(body.accessToken, body.refreshToken, body.expiresIn)

                // Cache user in Room for offline access
                database.userDao().insertUser(
                    UserEntity(
                        userId = user.userId,
                        tenantId = user.tenantId,
                        phone = user.phone,
                        name = user.name,
                        role = user.role,
                        status = user.status,
                        factoryIds = Json.encodeToString(user.factoryIds),
                        permissions = Json.encodeToString(user.permissions),
                        lastSyncTimestamp = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis()
                    )
                )

                // Cache permissions
                val cachedPermissions = user.permissions.map { perm ->
                    CachedPermission(
                        userId = user.userId,
                        module = perm.module,
                        action = perm.action,
                        resourceScope = perm.resourceScope,
                        cachedAt = System.currentTimeMillis()
                    )
                }
                database.permissionDao().deletePermissions(user.userId)
                database.permissionDao().insertPermissions(cachedPermissions)

                AuthResult.Success(
                    user = User(
                        userId = user.userId,
                        tenantId = user.tenantId,
                        phone = user.phone,
                        name = user.name,
                        role = user.role,
                        factoryIds = user.factoryIds,
                        allowedModules = extractAllowedModulesFromPermissions(user.permissions),
                    ),
                    offlineMode = false
                )
            } else {
                val errorBody = response.errorBody()?.string()
                AuthResult.Error(errorBody ?: "Authentication failed (${response.code()})")
            }
        } catch (e: Exception) {
            // Network error — fall back to offline
            loginOffline(phone, pin)
        }
    }

    private suspend fun loginOffline(phone: String, pin: String): AuthResult {
        val userEntity = database.userDao().getUserByPhone(phone)
            ?: return AuthResult.Error("User not found. Online authentication required for first login.")

        if (userEntity.status == "locked" || userEntity.status == "suspended") {
            return AuthResult.Error("Account is ${userEntity.status}. Contact your supervisor.")
        }

        // Queue successful offline login event for later sync
        database.offlineAuthDao().insertEvent(
            OfflineAuthEvent(
                eventId = UUID.randomUUID().toString(),
                userId = userEntity.userId,
                eventType = "login",
                timestamp = System.currentTimeMillis(),
                metadata = """{"phone":"$phone","offline":true}"""
            )
        )

        val factoryIds: List<String> = try {
            Json.decodeFromString(userEntity.factoryIds)
        } catch (_: Exception) {
            emptyList()
        }

        val allowedModules = database.permissionDao()
            .getPermissions(userEntity.userId)
            .map { it.module.trim().lowercase() }
            .filter { mobileModules.contains(it) }
            .distinct()

        return AuthResult.Success(
            user = User(
                userId = userEntity.userId,
                tenantId = userEntity.tenantId,
                phone = userEntity.phone,
                name = userEntity.name,
                role = userEntity.role,
                factoryIds = factoryIds,
                allowedModules = allowedModules,
            ),
            offlineMode = true
        )
    }

    suspend fun refreshToken(): Boolean {
        val refresh = tokenManager.getRefreshToken() ?: return false
        return try {
            val response = authApi.refreshToken(RefreshTokenRequest(refresh))
            if (response.isSuccessful) {
                val body = response.body() ?: return false
                tokenManager.saveTokens(body.accessToken, body.refreshToken, body.expiresIn)
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun logout() {
        try {
            val token = tokenManager.getAccessToken()
            if (token != null && connectivityObserver.isOnline()) {
                authApi.logout("Bearer $token")
            }
        } catch (_: Exception) {
            // Best-effort server logout
        }
        tokenManager.clearTokens()
    }

    private fun extractAllowedModulesFromPermissions(
        permissions: List<com.example.gudgum_prod_flow.data.remote.dto.PermissionDto>,
    ): List<String> {
        return permissions
            .map { it.module.trim().lowercase() }
            .filter { mobileModules.contains(it) }
            .distinct()
    }
}
