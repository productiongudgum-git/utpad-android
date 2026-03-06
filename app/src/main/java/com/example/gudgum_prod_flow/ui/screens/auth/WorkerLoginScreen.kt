package com.example.gudgum_prod_flow.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gudgum_prod_flow.ui.theme.ManuFlowBlue
import com.example.gudgum_prod_flow.ui.theme.ManuFlowError
import com.example.gudgum_prod_flow.ui.theme.SuccessGreen
import com.example.gudgum_prod_flow.ui.viewmodels.AuthViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.DemoWorkerCredential
import com.example.gudgum_prod_flow.ui.viewmodels.LoginState

@Composable
fun WorkerLoginScreen(
    onLoginSuccess: (String) -> Unit,
    onForgotPin: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel(),
) {
    val phone by authViewModel.phone.collectAsState()
    val pin by authViewModel.pin.collectAsState()
    val loginState by authViewModel.loginState.collectAsState()
    val rememberDevice by authViewModel.rememberDevice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val demoCredentials = remember { AuthViewModel.DemoCredentials }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginState.Success -> {
                onLoginSuccess(state.authorizedRoute)
                authViewModel.consumeLoginSuccess()
            }
            is LoginState.Error -> snackbarHostState.showSnackbar(state.message)
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        text = "Utpad Worker Login",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Single-factory mobile app for Inwarding, Production, Packing, and Dispatch.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            if (it.length <= 10 && it.all(Char::isDigit)) {
                                authViewModel.onPhoneChanged(it)
                            }
                        },
                        label = { Text("Worker phone number") },
                        prefix = { Text("+91 ", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = loginState !is LoginState.Loading && loginState !is LoginState.Locked,
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Enter your 6-digit PIN",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))

                    PinDotsRow(
                        enteredCount = pin.length,
                        totalCount = 6,
                        isError = loginState is LoginState.Error,
                        isSuccess = loginState is LoginState.Success,
                    )

                    if (loginState is LoginState.Error) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            (loginState as LoginState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    NumericKeypad(
                        onDigitPressed = authViewModel::onPinDigit,
                        onBackspacePressed = authViewModel::onPinBackspace,
                        onSubmitPressed = authViewModel::submitLogin,
                        submitEnabled = pin.length == 6 && loginState !is LoginState.Loading,
                        isLoading = loginState is LoginState.Loading,
                        enabled = loginState !is LoginState.Locked,
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = rememberDevice,
                            onCheckedChange = authViewModel::onRememberDeviceChanged,
                            enabled = loginState !is LoginState.Locked,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Remember this device", style = MaterialTheme.typography.bodyMedium)
                    }

                    TextButton(onClick = onForgotPin) {
                        Text("Forgot PIN?")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            DemoCredentialsCard(
                credentials = demoCredentials,
                onUseCredential = authViewModel::useDemoCredential,
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DemoCredentialsCard(
    credentials: List<DemoWorkerCredential>,
    onUseCredential: (DemoWorkerCredential) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Test Credentials",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Tap any credential to auto-fill login values.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            credentials.forEach { credential ->
                AssistChip(
                    onClick = { onUseCredential(credential) },
                    label = {
                        Text("${credential.label}: ${credential.phone} / ${credential.pin}")
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun PinDotsRow(
    enteredCount: Int,
    totalCount: Int,
    isError: Boolean,
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics {
            contentDescription = "$enteredCount of $totalCount digits entered"
        },
    ) {
        repeat(totalCount) { index ->
            val isFilled = index < enteredCount
            val dotColor by animateColorAsState(
                targetValue = when {
                    isError -> ManuFlowError
                    isSuccess -> SuccessGreen
                    isFilled -> ManuFlowBlue
                    else -> Color.Transparent
                },
                animationSpec = tween(150, easing = FastOutSlowInEasing),
                label = "dotColor",
            )
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1f else 0.85f,
                animationSpec = tween(150, easing = FastOutSlowInEasing),
                label = "dotScale",
            )
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (!isFilled && !isError && !isSuccess) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        } else {
                            Modifier
                        },
                    ),
            )
        }
    }
}

@Composable
fun NumericKeypad(
    onDigitPressed: (Int) -> Unit,
    onBackspacePressed: () -> Unit,
    onSubmitPressed: () -> Unit,
    submitEnabled: Boolean,
    isLoading: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val keys = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
        listOf(-1, 0, -2),
    )
    val alpha = if (enabled) 1f else 0.4f

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    when (key) {
                        -1 -> {
                            KeypadKey(
                                onClick = onBackspacePressed,
                                enabled = enabled,
                                alpha = alpha,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentDescription = "Backspace",
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        -2 -> {
                            KeypadKey(
                                onClick = onSubmitPressed,
                                enabled = enabled && submitEnabled,
                                alpha = if (enabled && submitEnabled) 1f else 0.4f,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentDescription = "Submit",
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                            }
                        }

                        else -> {
                            KeypadKey(
                                onClick = { onDigitPressed(key) },
                                enabled = enabled,
                                alpha = alpha,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentDescription = "Digit $key",
                            ) {
                                Text(
                                    text = key.toString(),
                                    style = MaterialTheme.typography.displayLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadKey(
    onClick: () -> Unit,
    enabled: Boolean,
    alpha: Float,
    containerColor: Color,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor.copy(alpha = alpha))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
