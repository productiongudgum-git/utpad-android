package com.example.gudgum_prod_flow.ui.screens.production

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.ui.components.BarcodeScannerButton
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.viewmodels.DispatchViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: DispatchViewModel = hiltViewModel(),
) {
    val currentStock by viewModel.currentStock.collectAsState()
    val batchCode by viewModel.batchCode.collectAsState()
    val qtyTakenFromPacking by viewModel.qtyTakenFromPacking.collectAsState()
    val qtyDispatched by viewModel.qtyDispatched.collectAsState()
    val remainingBalance by viewModel.remainingBalance.collectAsState()
    val isPending by viewModel.isPending.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val vehicleNumber by viewModel.vehicleNumber.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is SubmitState.Success -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearSubmitState()
            }
            is SubmitState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearSubmitState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dispatch", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Manage outbound stock & shipments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 132.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OperationsModuleTabs(
                    currentRoute = AppRoute.Dispatch,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                // Current Stock Card (always visible)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Current Stock",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Available in Packing Floor",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = "%,d".format(currentStock),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = " units",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }

                // Wizard step label
                Text(
                    text = "STEP $currentStep OF 3",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )

                when (currentStep) {
                    // ── Step 1: Batch Code ──
                    1 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Batch Details",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Batch Code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = batchCode,
                                        onValueChange = { viewModel.onBatchCodeChanged(it) },
                                        placeholder = { Text("Scan or enter code") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BarcodeScannerButton(
                                        prompt = "Scan dispatch batch barcode",
                                        onBarcodeScanned = viewModel::onBatchCodeChanged,
                                        onScanError = { message ->
                                            if (message != "Scan cancelled") {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(message)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // ── Step 2: Dispatch Quantities ──
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    text = "Dispatch Entry",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                // Qty Taken from Packing
                                Column {
                                    Text(
                                        text = "Qty Taken from Packing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = qtyTakenFromPacking,
                                        onValueChange = { viewModel.onQtyTakenChanged(it) },
                                        placeholder = { Text("0") },
                                        suffix = { Text("kg") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // Qty to Dispatch
                                Column {
                                    Text(
                                        text = "Qty Utilized / Dispatched",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = qtyDispatched,
                                        onValueChange = { viewModel.onQtyDispatchedChanged(it) },
                                        placeholder = { Text("0") },
                                        suffix = { Text("kg") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    // ── Step 3: Destination, Vehicle & Review ──
                    3 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    text = "Shipping Details",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                // Destination
                                Column {
                                    Text(
                                        text = "Destination",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = destination,
                                        onValueChange = { viewModel.onDestinationChanged(it) },
                                        placeholder = { Text("Enter destination address") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                // Vehicle Number
                                Column {
                                    Text(
                                        text = "Vehicle Number",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = vehicleNumber,
                                        onValueChange = { viewModel.onVehicleNumberChanged(it) },
                                        placeholder = { Text("e.g. MH-12-AB-1234") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // Status / Balance Review Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Status Result",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    val statusColor = if (isPending) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                    val statusContainerColor = if (isPending) {
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = statusContainerColor,
                                    ) {
                                        Text(
                                            text = if (isPending) "PENDING" else "DISPATCHED",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    Text(
                                        text = "Remaining Balance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        val balanceColor = if (remainingBalance < 100) {
                                            MaterialTheme.colorScheme.tertiary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                        Text(
                                            text = "%,d".format(remainingBalance),
                                            style = MaterialTheme.typography.headlineSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                            ),
                                            color = balanceColor,
                                        )
                                        Text(
                                            text = " kg",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom action bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 10.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (currentStep > 1) {
                                    viewModel.previousStep()
                                } else {
                                    viewModel.reset()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            if (currentStep > 1) {
                                Text("Back")
                            } else {
                                Text("Reset")
                            }
                        }

                        Button(
                            onClick = {
                                if (currentStep < 3) {
                                    viewModel.nextStep()
                                } else {
                                    viewModel.submit()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = submitState !is SubmitState.Loading,
                        ) {
                            if (currentStep < 3) {
                                Text("Next")
                            } else {
                                Text("Confirm Dispatch")
                            }
                        }
                    }
                    if (AppRoute.Inwarding in allowedRoutes) {
                        TextButton(
                            onClick = { onNavigateToRoute(AppRoute.Inwarding) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Back to Inwarding")
                        }
                    }
                }
            }
        }
    }
}
