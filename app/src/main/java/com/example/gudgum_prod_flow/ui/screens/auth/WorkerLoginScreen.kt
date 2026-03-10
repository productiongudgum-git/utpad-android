package com.example.gudgum_prod_flow.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gudgum_prod_flow.ui.theme.UtpadError
import com.example.gudgum_prod_flow.ui.theme.UtpadOutline
import com.example.gudgum_prod_flow.ui.theme.UtpadPrimary
import com.example.gudgum_prod_flow.ui.theme.UtpadSuccess
import com.example.gudgum_prod_flow.ui.theme.UtpadTextPrimary
import com.example.gudgum_prod_flow.ui.viewmodels.AuthViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.LoginState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerLoginScreen(
    onLoginSuccess: (String) -> Unit,
    onForgotPin: () -> Unit = {},
    authViewModel: AuthViewModel = viewModel(),
) {
    val phone by authViewModel.phone.collectAsState()
    val pin by authViewModel.pin.collectAsState()
    val loginState by authViewModel.loginState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentStep by remember { mutableIntStateOf(1) } // 1 for Phone, 2 for PIN

    BackHandler(enabled = currentStep == 2) {
        currentStep = 1
    }

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
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Use the drawable directly for the logo
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.gudgum_prod_flow.R.drawable.gudgum_logo),
                        contentDescription = "GudGum Logo",
                        modifier = Modifier.height(48.dp)
                    )
                },
                navigationIcon = {
                    if (currentStep == 2) {
                        IconButton(onClick = { currentStep = 1 }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = UtpadTextPrimary)
                        }
                    } else {
                        // Empty box to keep title centered
                        Box(Modifier.size(48.dp))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Crossfade(targetState = currentStep, label = "LoginStepTransition") { step ->
            when (step) {
                1 -> PhoneInputStep(
                    modifier = Modifier.padding(padding),
                    phone = phone,
                    onPhoneChanged = { newPhone -> 
                        authViewModel.onPhoneChanged(newPhone)
                    },
                    onNextStep = { currentStep = 2 }
                )
                2 -> PinInputStep(
                    modifier = Modifier.padding(padding),
                    phone = phone,
                    pin = pin,
                    onPinChanged = authViewModel::onPinChanged,
                    onSubmit = authViewModel::submitLogin,
                    isLoading = loginState is LoginState.Loading,
                    isError = loginState is LoginState.Error,
                    isSuccess = loginState is LoginState.Success,
                    errorMessage = (loginState as? LoginState.Error)?.message
                )
            }
        }
    }
}

@Composable
fun PhoneInputStep(
    modifier: Modifier = Modifier,
    phone: String,
    onPhoneChanged: (String) -> Unit,
    onNextStep: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter your phone\nnumber",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = UtpadTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Phone Number",
            style = MaterialTheme.typography.labelMedium,
            color = Color.DarkGray,
            modifier = Modifier.fillMaxWidth(),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        androidx.compose.material3.OutlinedTextField(
            value = phone,
            onValueChange = { newValue ->
                val digitsOnly = newValue.filter { it.isDigit() }
                if (digitsOnly.length <= 10) {
                    onPhoneChanged(digitsOnly)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = {
                    if (phone.length == 10) onNextStep()
                }
            ),
            singleLine = true,
            leadingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp)
                ) {
                    Text("🇮🇳", fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("+91", fontWeight = FontWeight.SemiBold, color = UtpadTextPrimary, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFFCBD5E1)))
                }
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF8FAFC),
                unfocusedContainerColor = Color(0xFFF8FAFC),
                focusedBorderColor = UtpadPrimary,
                unfocusedBorderColor = Color(0xFFE2E8F0),
            ),
            shape = RoundedCornerShape(16.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp,
                letterSpacing = 1.sp,
                color = UtpadTextPrimary,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onNextStep,
            enabled = phone.length == 10,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(if (phone.length == 10) 8.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = UtpadPrimary.copy(alpha = 0.5f)),
            colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Next Step", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "By continuing, you agree to our Terms of Service and Privacy Policy. Data rates may apply.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun PinInputStep(
    modifier: Modifier = Modifier,
    phone: String,
    pin: String,
    onPinChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
    isError: Boolean,
    isSuccess: Boolean,
    errorMessage: String?
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Enter your 6-digit\nPIN",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = UtpadTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        val displayPhone = buildString {
            append("Sent to +91 ")
            for (i in 0 until phone.length) {
                append(phone[i])
                if (i == 2 || i == 5) append(" ")
            }
        }
        
        Text(
            text = displayPhone,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(32.dp))

        PinCodeInput(
            pin = pin,
            onPinChanged = onPinChanged,
            totalCount = 6,
            isError = isError,
            isSuccess = isSuccess,
            enabled = !isLoading,
        )

        if (isError && errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = UtpadError,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }



        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onSubmit,
            enabled = pin.length == 6 && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(if (pin.length == 6 && !isLoading) 8.dp else 0.dp, RoundedCornerShape(16.dp), spotColor = UtpadPrimary.copy(alpha = 0.5f)),
            colors = ButtonDefaults.buttonColors(containerColor = UtpadPrimary),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 3.dp)
            } else {
                Text("Verify and Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun NumericKeypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    bottomLeftIcon: @Composable () -> Unit,
    enabled: Boolean = true
) {
    val keys = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
        listOf(-1, 0, -2) // -1 is bottom left, -2 is backspace
    )
    
    val alpha = if (enabled) 1f else 0.4f

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        -1 -> {
                            KeypadButton(
                                onClick = { /* Do nothing for bottom left */ },
                                enabled = false,
                                containerColor = Color.Transparent,
                                hasShadow = false,
                                content = bottomLeftIcon
                            )
                        }
                        -2 -> {
                            KeypadButton(
                                onClick = onBackspace,
                                enabled = enabled,
                                alpha = alpha,
                                content = {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", tint = UtpadTextPrimary)
                                }
                            )
                        }
                        else -> {
                            KeypadButton(
                                onClick = { onDigit(key) },
                                enabled = enabled,
                                alpha = alpha,
                                content = {
                                    Text(key.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = UtpadTextPrimary)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.KeypadButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    alpha: Float = 1f,
    containerColor: Color = Color.White,
    hasShadow: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(64.dp)
            .then(
                if (hasShadow) Modifier.shadow(4.dp, RoundedCornerShape(16.dp), spotColor = Color(0x0F000000), ambientColor = Color(0x05000000))
                else Modifier
            )
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor.copy(alpha = alpha))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun PinCodeInput(
    pin: String,
    onPinChanged: (String) -> Unit,
    totalCount: Int,
    isError: Boolean,
    isSuccess: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "${pin.length} of $totalCount digits entered" },
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = pin,
            onValueChange = { value ->
                if (value.all(Char::isDigit) && value.length <= totalCount) {
                    onPinChanged(value)
                }
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
            modifier = Modifier
                .focusRequester(focusRequester)
                .size(1.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
        ) {
            repeat(totalCount) { index ->
                val value = pin.getOrNull(index)?.toString() ?: ""
                val borderColor by animateColorAsState(
                    targetValue = when {
                        isError -> UtpadError
                        isSuccess -> UtpadSuccess
                        index < pin.length -> UtpadPrimary
                        else -> UtpadOutline
                    },
                    animationSpec = tween(150, easing = FastOutSlowInEasing),
                    label = "pinBorderColor",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(2.dp, borderColor, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = UtpadTextPrimary,
                    )
                }
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
                    isError -> UtpadError
                    isSuccess -> UtpadSuccess
                    isFilled -> UtpadPrimary
                    else -> UtpadOutline
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
                    .background(dotColor),
            )
        }
    }
}
