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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import com.example.gudgum_prod_flow.ui.components.BarcodeScannerButton
import com.example.gudgum_prod_flow.ui.navigation.AppRoute
import com.example.gudgum_prod_flow.ui.viewmodels.InwardingViewModel
import com.example.gudgum_prod_flow.ui.viewmodels.SubmitState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InwardingScreen(
    allowedRoutes: Set<String>,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToRoute: (String) -> Unit,
    viewModel: InwardingViewModel = hiltViewModel(),
) {
    val selectedIngredient by viewModel.selectedIngredient.collectAsState()
    val batchBarcode by viewModel.batchBarcode.collectAsState()
    val quantity by viewModel.quantity.collectAsState()
    val selectedUnit by viewModel.selectedUnit.collectAsState()
    val expiryDate by viewModel.expiryDate.collectAsState()
    val selectedVendor by viewModel.selectedVendor.collectAsState()
    val billNumber by viewModel.billNumber.collectAsState()
    val submitState by viewModel.submitState.collectAsState()
    val currentStep by viewModel.currentWizardStep.collectAsState()
    val availableIngredients by viewModel.availableIngredients.collectAsState()

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

            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Inwarding Material", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Raw material entry and billing",
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
                    currentRoute = AppRoute.Inwarding,
                    allowedRoutes = allowedRoutes,
                    onNavigateToRoute = onNavigateToRoute,
                )

                SectionTitle("Step $currentStep of 3")

                when (currentStep) {
                    1 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Column {
                                    Text(
                                        text = "Select Ingredient",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        var ingredientExpanded by remember { mutableStateOf(false) }
                                        ExposedDropdownMenuBox(
                                            expanded = ingredientExpanded,
                                            onExpandedChange = { ingredientExpanded = it },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            OutlinedTextField(
                                                value = selectedIngredient?.name ?: "",
                                                onValueChange = {},
                                                readOnly = true,
                                                placeholder = { Text("Choose ingredient") },
                                                trailingIcon = {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = ingredientExpanded)
                                                },
                                                modifier = Modifier
                                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                    .fillMaxWidth(),
                                                singleLine = true,
                                            )
                                            ExposedDropdownMenu(
                                                expanded = ingredientExpanded,
                                                onDismissRequest = { ingredientExpanded = false },
                                            ) {
                                                if (availableIngredients.isEmpty()) {
                                                    DropdownMenuItem(
                                                        text = { Text("No ingredients for this vendor") },
                                                        onClick = { ingredientExpanded = false },
                                                        enabled = false
                                                    )
                                                } else {
                                                    availableIngredients.forEach { ingredient ->
                                                        DropdownMenuItem(
                                                            text = { Text(ingredient.name) },
                                                            onClick = {
                                                                viewModel.onIngredientSelected(ingredient)
                                                                ingredientExpanded = false
                                                            },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                            },
                                            modifier = Modifier.size(52.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Add ingredient",
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = "Vendor",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    var vendorExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = vendorExpanded,
                                        onExpandedChange = { vendorExpanded = it },
                                    ) {
                                        OutlinedTextField(
                                            value = selectedVendor?.name ?: "",
                                            onValueChange = {},
                                            readOnly = true,
                                            placeholder = { Text("Select vendor...") },
                                            trailingIcon = {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = vendorExpanded)
                                            },
                                            modifier = Modifier
                                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                                .fillMaxWidth(),
                                            singleLine = true,
                                        )
                                        ExposedDropdownMenu(
                                            expanded = vendorExpanded,
                                            onDismissRequest = { vendorExpanded = false },
                                        ) {
                                            viewModel.vendors.forEach { vendor ->
                                                DropdownMenuItem(
                                                    text = { Text(vendor.name) },
                                                    onClick = {
                                                        viewModel.onVendorSelected(vendor)
                                                        vendorExpanded = false
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                // Removed Select Ingredient
                        Column {
                            Text(
                                text = "Batch Barcode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                OutlinedTextField(
                                    value = batchBarcode,
                                    onValueChange = viewModel::onBarcodeChanged,
                                    placeholder = { Text("Scan or enter barcode") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                BarcodeScannerButton(
                                    prompt = "Scan inwarding batch barcode",
                                    onBarcodeScanned = viewModel::onBarcodeChanged,
                                    onScanError = { message ->
                                        if (message != "Scan cancelled") {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(message)
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quantity",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = quantity,
                                    onValueChange = viewModel::onQuantityChanged,
                                    placeholder = { Text("0.00") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Unit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                var unitExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = unitExpanded,
                                    onExpandedChange = { unitExpanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = selectedUnit,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded)
                                        },
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                            .fillMaxWidth(),
                                        singleLine = true,
                                    )
                                    ExposedDropdownMenu(
                                        expanded = unitExpanded,
                                        onDismissRequest = { unitExpanded = false },
                                    ) {
                                        viewModel.units.forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(unit) },
                                                onClick = {
                                                    viewModel.onUnitSelected(unit)
                                                    unitExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column {
                            Text(
                                text = "Expiry Date",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            var showDatePicker by remember { mutableStateOf(false) }
                            
                            OutlinedTextField(
                                value = expiryDate,
                                onValueChange = viewModel::onExpiryDateChanged,
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
                                                viewModel.onExpiryDateChanged(formatter.format(Date(selectedMillis)))
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
            3 -> {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column {
                            Text(
                                text = "Bill Number",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = billNumber,
                                onValueChange = viewModel::onBillNumberChanged,
                                placeholder = { Text("Enter bill number") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tap to capture or upload bill image",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                                Text("Confirm Inward")
                            }
                        }
                    }
                    if (AppRoute.Production in allowedRoutes) {
                        TextButton(
                            onClick = { onNavigateToRoute(AppRoute.Production) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Continue to Production")
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
