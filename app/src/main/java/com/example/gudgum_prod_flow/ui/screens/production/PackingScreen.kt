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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.viewmodels.PackingViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackingScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: PackingViewModel = hiltViewModel(),
) {
    val selectedBatch by viewModel.selectedBatch.collectAsState()
    val selectedSku by viewModel.selectedSku.collectAsState()
    val availableSkus by viewModel.availableSkus.collectAsState()
    val qtyPacked by viewModel.qtyPacked.collectAsState()
    val boxesMade by viewModel.boxesMade.collectAsState()
    val packingDate by viewModel.packingDate.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val shiftSummary by viewModel.shiftSummary.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

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
                        Text("Packing", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Pack finished goods into boxes",
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
                    currentRoute = AppRoute.Packing,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                // Process flow badge row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "Semi-finished",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Packing",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Wizard step label
                Text(
                    text = "STEP $currentStep OF 3",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )

                when (currentStep) {
                    // ── Step 1: Select Batch & SKU ──
                    1 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                // Semi-finished Batch Code dropdown
                                Column {
                                    Text(
                                        text = "Semi-finished Batch Code",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var batchExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = batchExpanded,
                                        onExpandedChange = { batchExpanded = it },
                                    ) {
                                        OutlinedTextField(
                                            value = selectedBatch,
                                            onValueChange = {},
                                            readOnly = true,
                                            placeholder = { Text("Select Batch...") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = batchExpanded) },
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth(),
                                            singleLine = true,
                                        )
                                        ExposedDropdownMenu(
                                            expanded = batchExpanded,
                                            onDismissRequest = { batchExpanded = false },
                                        ) {
                                            viewModel.batches.forEach { batch ->
                                                DropdownMenuItem(
                                                    text = { Text(batch) },
                                                    onClick = {
                                                        viewModel.onBatchSelected(batch)
                                                        batchExpanded = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }

                                // Packing SKU dropdown (filtered by batch)
                                Column {
                                    Text(
                                        text = "Packing SKU",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var skuExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = skuExpanded,
                                        onExpandedChange = { skuExpanded = it },
                                    ) {
                                        OutlinedTextField(
                                            value = selectedSku?.name ?: "",
                                            onValueChange = {},
                                            readOnly = true,
                                            placeholder = { Text("Select Product SKU...") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = skuExpanded) },
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth(),
                                            singleLine = true,
                                        )
                                        ExposedDropdownMenu(
                                            expanded = skuExpanded,
                                            onDismissRequest = { skuExpanded = false },
                                        ) {
                                            if (availableSkus.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text("No SKUs for this batch") },
                                                    onClick = { skuExpanded = false },
                                                    enabled = false,
                                                )
                                            } else {
                                                availableSkus.forEach { sku ->
                                                    DropdownMenuItem(
                                                        text = { Text(sku.name) },
                                                        onClick = {
                                                            viewModel.onSkuSelected(sku)
                                                            skuExpanded = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Step 2: Qty Packed, Boxes Made, Packing Date ──
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                // Qty Packed + Boxes Made grid
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Qty Packed",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = qtyPacked,
                                            onValueChange = { viewModel.onQtyPackedChanged(it) },
                                            placeholder = { Text("0") },
                                            suffix = { Text("kg") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Boxes Made",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = boxesMade,
                                            onValueChange = { viewModel.onBoxesMadeChanged(it) },
                                            placeholder = { Text("0") },
                                            suffix = { Text("units") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }

                                // Date of Packing - Smart Date Picker
                                Column {
                                    Text(
                                        text = "Date of Packing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var showDatePicker by remember { mutableStateOf(false) }

                                    OutlinedTextField(
                                        value = packingDate,
                                        onValueChange = {},
                                        placeholder = { Text("YYYY-MM-DD") },
                                        singleLine = true,
                                        readOnly = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Button(onClick = { showDatePicker = true }, modifier = Modifier.padding(top = 8.dp)) {
                                        Text("Pick Date")
                                    }

                                    if (showDatePicker) {
                                        val datePickerState = rememberDatePickerState()
                                        DatePickerDialog(
                                            onDismissRequest = { showDatePicker = false },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    val selectedMillis = datePickerState.selectedDateMillis
                                                    if (selectedMillis != null) {
                                                        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                        viewModel.onPackingDateChanged(formatter.format(Date(selectedMillis)))
                                                    }
                                                    showDatePicker = false
                                                }) {
                                                    Text("OK")
                                                }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showDatePicker = false }) {
                                                    Text("Cancel")
                                                }
                                            }
                                        ) {
                                            DatePicker(state = datePickerState)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Step 3: Notes & Shift Summary Review ──
                    3 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                // Notes
                                Column {
                                    Text(
                                        text = "Notes (Optional)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = { viewModel.onNotesChanged(it) },
                                        placeholder = { Text("Any issues during packing?") },
                                        maxLines = 3,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }

                        // Shift Summary Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column {
                                    Text(
                                        text = "Shift Summary",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${shiftSummary.shift} shift - Total packed: ${shiftSummary.totalPacked} kg, ${shiftSummary.totalBoxes} boxes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
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
                                    viewModel.clear()
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
                                Text("Submit Packing")
                            }
                        }
                    }
                    if (AppRoute.Dispatch in allowedRoutes) {
                        TextButton(
                            onClick = { onNavigateToRoute(AppRoute.Dispatch) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue to Dispatch")
                        }
                    }
                }
            }
        }
    }
}
