package com.example.gudgum_prod_flow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.gudgum_prod_flow.ui.screens.auth.PinResetScreen
import com.example.gudgum_prod_flow.ui.screens.auth.WorkerLoginScreen
import com.example.gudgum_prod_flow.ui.screens.production.DispatchScreen
import com.example.gudgum_prod_flow.ui.screens.production.InwardingScreen
import com.example.gudgum_prod_flow.ui.screens.production.PackingScreen
import com.example.gudgum_prod_flow.ui.screens.production.ProductionScreen
import com.example.gudgum_prod_flow.ui.viewmodels.AuthViewModel

@Composable
fun UtpadNavGraph(navController: NavHostController) {
    val authViewModel: AuthViewModel = viewModel()
    val workerSession by authViewModel.workerSession.collectAsState()
    val allowedRoutes = workerSession?.authorizedRoutes ?: emptySet()

    fun navigateToAuthorizedRoute(route: String) {
        if (route in allowedRoutes) {
            navController.navigate(route) { launchSingleTop = true }
        }
    }

    fun navigateBackToLogin() {
        navController.navigate(AppRoute.WorkerLogin) {
            launchSingleTop = true
        }
    }

    fun logoutAndNavigateToLogin() {
        authViewModel.logout()
        navController.navigate(AppRoute.WorkerLogin) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.WorkerLogin,
    ) {
        composable(AppRoute.WorkerLogin) {
            WorkerLoginScreen(
                onLoginSuccess = { authorizedRoute ->
                    navController.navigate(authorizedRoute) {
                        popUpTo(AppRoute.WorkerLogin) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                authViewModel = authViewModel,
            )
        }

        composable(AppRoute.PinReset) {
            PinResetScreen(onBackPressed = { navController.popBackStack() })
        }

        composable(AppRoute.Inwarding) {
            val canAccess = AppRoute.Inwarding in allowedRoutes
            LaunchedEffect(workerSession, canAccess) {
                if (workerSession == null) {
                    navController.navigate(AppRoute.WorkerLogin) { launchSingleTop = true }
                } else if (!canAccess) {
                    navController.navigate(authViewModel.authorizedHomeRoute()) { launchSingleTop = true }
                }
            }

            if (canAccess) {
                InwardingScreen(
                    allowedRoutes = allowedRoutes,
                    onBack = ::navigateBackToLogin,
                    onLogout = ::logoutAndNavigateToLogin,
                    onNavigateToRoute = ::navigateToAuthorizedRoute,
                )
            }
        }

        composable(AppRoute.Production) {
            val canAccess = AppRoute.Production in allowedRoutes
            LaunchedEffect(workerSession, canAccess) {
                if (workerSession == null) {
                    navController.navigate(AppRoute.WorkerLogin) { launchSingleTop = true }
                } else if (!canAccess) {
                    navController.navigate(authViewModel.authorizedHomeRoute()) { launchSingleTop = true }
                }
            }

            if (canAccess) {
                ProductionScreen(
                    allowedRoutes = allowedRoutes,
                    onBack = ::navigateBackToLogin,
                    onLogout = ::logoutAndNavigateToLogin,
                    onNavigateToRoute = ::navigateToAuthorizedRoute,
                )
            }
        }

        composable(AppRoute.Packing) {
            val canAccess = AppRoute.Packing in allowedRoutes
            LaunchedEffect(workerSession, canAccess) {
                if (workerSession == null) {
                    navController.navigate(AppRoute.WorkerLogin) { launchSingleTop = true }
                } else if (!canAccess) {
                    navController.navigate(authViewModel.authorizedHomeRoute()) { launchSingleTop = true }
                }
            }

            if (canAccess) {
                PackingScreen(
                    allowedRoutes = allowedRoutes,
                    onBack = ::navigateBackToLogin,
                    onLogout = ::logoutAndNavigateToLogin,
                    onNavigateToRoute = ::navigateToAuthorizedRoute,
                )
            }
        }

        composable(AppRoute.Dispatch) {
            val canAccess = AppRoute.Dispatch in allowedRoutes
            LaunchedEffect(workerSession, canAccess) {
                if (workerSession == null) {
                    navController.navigate(AppRoute.WorkerLogin) { launchSingleTop = true }
                } else if (!canAccess) {
                    navController.navigate(authViewModel.authorizedHomeRoute()) { launchSingleTop = true }
                }
            }

            if (canAccess) {
                DispatchScreen(
                    allowedRoutes = allowedRoutes,
                    onBack = ::navigateBackToLogin,
                    onLogout = ::logoutAndNavigateToLogin,
                    onNavigateToRoute = ::navigateToAuthorizedRoute,
                )
            }
        }
    }
}
