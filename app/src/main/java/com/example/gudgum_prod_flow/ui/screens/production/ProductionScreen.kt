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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.viewmodels.ProductionViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductionScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: ProductionViewModel = hiltViewModel(),
) {
    val selectedFlavor by viewModel.selectedFlavor.collectAsState()
    val batchCode by viewModel.batchCode.collectAsState()
    val recipe by viewModel.recipe.collectAsState()
    val expectedYield by viewModel.expectedYield.collectAsState()
    val manufacturingDate by viewModel.manufacturingDate.collectAsState()
    val actualOutput by viewModel.actualOutput.collectAsState()
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
                        Text("New Production", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Raw Material to Semifinished Goods",
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
                    .padding(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                OperationsModuleTabs(
                    currentRoute = AppRoute.Production,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                SectionTitle("Step $currentStep of 3")

                when (currentStep) {
                    1 -> {
                        // Flavor Profile + Batch Code card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Flavor Profile dropdown
                        Column {
                            Text(
                                text = "Flavor Profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var flavorExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = flavorExpanded,
                                onExpandedChange = { flavorExpanded = it },
                            ) {
                                OutlinedTextField(
                                    value = selectedFlavor?.name ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    placeholder = { Text("Select Flavor...") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = flavorExpanded) },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    singleLine = true,
                                )
                                ExposedDropdownMenu(
                                    expanded = flavorExpanded,
                                    onDismissRequest = { flavorExpanded = false },
                                ) {
                                    viewModel.flavorProfiles.forEach { flavor ->
                                        DropdownMenuItem(
                                            text = { Text(flavor.name) },
                                            onClick = {
                                                viewModel.onFlavorSelected(flavor)
                                                flavorExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        // Batch Code (read-only)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Batch Code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        text = "Autogenerated",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = batchCode,
                                onValueChange = {},
                                readOnly = true,
                                placeholder = { Text("Auto-generated on flavor select") },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            )
                        }
                    }
                }
                } // End of step 1
                2 -> {
                // Recipe Ingredients section
                Text(
                    text = "Recipe Ingredients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column {
                        // Header row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "ITEM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "QTY (KG)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()

                        // Ingredient rows
                        recipe.forEachIndexed { index, ingredient ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = ingredient.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = ingredient.quantity,
                                    onValueChange = { viewModel.onRecipeQuantityChanged(index, it) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        textAlign = TextAlign.End,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    modifier = Modifier
                                        .weight(0.5f)
                                        .height(48.dp),
                                )
                            }
                            if (index < recipe.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }

                        // Expected Yield footer
                        HorizontalDivider()
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Expected Yield",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = expectedYield,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                } // End of step 2
                3 -> {
                // Manufacturing Date + Actual Output card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Manufacturing Date
                        Column {
                            Text(
                                text = "Manufacturing Date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var showDatePicker by remember { mutableStateOf(false) }
                            if (showDatePicker) {
                                val datePickerState = rememberDatePickerState()
                                DatePickerDialog(
                                    onDismissRequest = { showDatePicker = false },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            datePickerState.selectedDateMillis?.let { millis ->
                                                val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                                viewModel.onManufacturingDateChanged(format.format(java.util.Date(millis)))
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
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = manufacturingDate,
                                    onValueChange = {},
                                    readOnly = true,
                                    placeholder = { Text("Select Date") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                )
                                // Invisible surface to capture clicks
                                Surface(
                                    modifier = Modifier.matchParentSize(),
                                    color = androidx.compose.ui.graphics.Color.Transparent,
                                    onClick = { showDatePicker = true }
                                ) {}
                            }
                        }

                        // Actual Production Output
                        Column {
                            Text(
                                text = "Actual Production Output (Kg)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = actualOutput,
                                onValueChange = { viewModel.onActualOutputChanged(it) },
                                placeholder = { Text("0.00") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                suffix = { Text("Kg") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "System allows +/-5% tolerance from expected yield.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                } // End of step 3
                } // End of when

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bottom action button
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        androidx.compose.material3.OutlinedButton(
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
                                Text("Confirm & Save")
                            }
                        }
                    }
                    if (AppRoute.Packing in allowedRoutes) {
                        TextButton(
                            onClick = { onNavigateToRoute(AppRoute.Packing) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue to Packing")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}
