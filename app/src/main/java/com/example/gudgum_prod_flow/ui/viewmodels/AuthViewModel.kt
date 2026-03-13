package com.example.gudgum_prod_flow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gudgum_prod_flow.data.session.WorkerIdentityStore
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DemoWorkerCredential(
    val label: String,
    val phone: String,
    val pin: String,
    val role: String,
)

data class WorkerSession(
    val workerLabel: String,
    val phone: String,
    val role: String,
    val authorizedRoutes: Set<String>,
    val homeRoute: String,
)

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val workerLabel: String, val authorizedRoute: String) : LoginState()
    data class Error(val message: String) : LoginState()
    data class Locked(val minutesRemaining: Int) : LoginState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: com.example.gudgum_prod_flow.domain.usecase.LoginUseCase
) : ViewModel() {

    // Removed DemoCredentials logic since we now use real authentication via Vercel/Supabase

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    private val _rememberDevice = MutableStateFlow(false)
    val rememberDevice: StateFlow<Boolean> = _rememberDevice.asStateFlow()

    private val _workerSession = MutableStateFlow<WorkerSession?>(null)
    val workerSession: StateFlow<WorkerSession?> = _workerSession.asStateFlow()

    fun onPhoneChanged(value: String) {
        _phone.value = value.trim()
        if (_loginState.value is LoginState.Error) {
            _loginState.value = LoginState.Idle
        }
    }

    fun onRememberDeviceChanged(rememberDevice: Boolean) {
        _rememberDevice.value = rememberDevice
    }

    fun submitLogin() {
        performLogin()
    }

    fun consumeLoginSuccess() {
        if (_loginState.value is LoginState.Success) {
            _loginState.value = LoginState.Idle
        }
    }

    fun authorizedHomeRoute(): String {
        return _workerSession.value?.homeRoute ?: AppRoute.WorkerLogin
    }

    private fun performLogin() {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading

            when (val result = loginUseCase(_phone.value, "")) {
                is com.example.gudgum_prod_flow.domain.model.AuthResult.Success -> {
                    val user = result.user
                    val allowedRoutes = routesForAssignment(user.allowedModules, user.role)
                    val homeRoute = allowedRoutes.firstOrNull()

                    if (homeRoute == null) {
                        _loginState.value = LoginState.Error(
                            "This user is not assigned to any mobile module.",
                        )
                        return@launch
                    }

                    _workerSession.value = WorkerSession(
                        workerLabel = user.name,
                        phone = user.phone,
                        role = user.role,
                        authorizedRoutes = allowedRoutes,
                        homeRoute = homeRoute,
                    )
                    WorkerIdentityStore.setIdentity(
                        phone = user.phone,
                        label = user.name,
                        role = user.role,
                    )
                    _loginState.value = LoginState.Success(
                        workerLabel = user.name,
                        authorizedRoute = homeRoute,
                    )
                }
                is com.example.gudgum_prod_flow.domain.model.AuthResult.Error -> {
                    _loginState.value = LoginState.Error(result.message)
                }
            }
        }
    }

    fun logout() {
        WorkerIdentityStore.clear()
        _workerSession.value = null
        _loginState.value = LoginState.Idle
        _phone.value = ""
    }

    private fun routesForAssignment(allowedModules: List<String>, role: String): Set<String> {
        val fromModules = allowedModules
            .map { it.trim().lowercase() }
            .mapNotNull { moduleToRoute(it) }
            .toCollection(linkedSetOf())

        if (fromModules.isNotEmpty()) {
            return fromModules
        }

        return roleToRoutes(role)
    }

    private fun roleToRoutes(role: String): Set<String> {
        return when (role.trim().lowercase().replace('-', '_').replace(' ', '_')) {
            "inwarding", "inwarding_staff" -> linkedSetOf(AppRoute.Inwarding)
            "production", "production_operator" -> linkedSetOf(AppRoute.Production)
            "packing", "packing_staff" -> linkedSetOf(AppRoute.Packing)
            "dispatch", "dispatch_staff" -> linkedSetOf(AppRoute.Dispatch)
            "factory_supervisor", "tenant_admin", "platform_admin" -> linkedSetOf(
                AppRoute.Inwarding,
                AppRoute.Production,
                AppRoute.Packing,
                AppRoute.Dispatch,
            )
            else -> emptySet()
        }
    }

    private fun moduleToRoute(module: String): String? {
        return when (module) {
            "inwarding" -> AppRoute.Inwarding
            "production" -> AppRoute.Production
            "packing" -> AppRoute.Packing
            "dispatch" -> AppRoute.Dispatch
            else -> null
        }
    }
}
