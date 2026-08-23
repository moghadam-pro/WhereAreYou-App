package com.whereareyou.app.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereareyou.core.model.EmergencyCallbackMode
import com.whereareyou.core.model.TrustedContactId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    repository: TrustedContactRepository,
    contactIdToEdit: TrustedContactId?,
    onDone: () -> Unit,
) {
    val viewModel: ContactEditViewModel = viewModel(
        factory = ContactEditViewModel.Factory(repository, contactIdToEdit),
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit trusted contact" else "Add trusted contact") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChanged,
                label = { Text("Display name") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.phoneNumber,
                onValueChange = viewModel::onPhoneNumberChanged,
                label = { Text("Phone number") },
                isError = state.phoneError != null,
                supportingText = { state.phoneError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.commandSecret,
                onValueChange = viewModel::onCommandSecretChanged,
                label = { Text("Shared SMS command code") },
                supportingText = { Text("Required before this contact can use an SMS status request.") },
                modifier = Modifier.fillMaxWidth(),
            )

            ToggleRow("Enabled", state.enabled, viewModel::onEnabledChanged)

            Text("Capabilities", style = MaterialTheme.typography.titleMedium)
            ToggleRow("Missed-call trigger", state.missedCallTrigger, viewModel::onMissedCallTriggerChanged)
            ToggleRow("SMS status command", state.smsCommand, viewModel::onSmsCommandChanged)
            ToggleRow("Receive low-battery alert", state.receiveLowBatteryAlert, viewModel::onReceiveLowBatteryAlertChanged)
            ToggleRow("Manual status recipient", state.manualStatusRecipient, viewModel::onManualStatusRecipientChanged)

            EmergencyCallbackDropdown(state.emergencyCallback, viewModel::onEmergencyCallbackChanged)

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }

            if (state.isEditing) {
                OutlinedButton(onClick = viewModel::delete, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove trusted contact")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmergencyCallbackDropdown(
    selected: EmergencyCallbackMode,
    onSelected: (EmergencyCallbackMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text("Emergency Callback (default: Off)", style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.label(),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                EmergencyCallbackMode.values().forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.label()) },
                        onClick = {
                            onSelected(mode)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun EmergencyCallbackMode.label(): String = when (this) {
    EmergencyCallbackMode.OFF -> "Off"
    EmergencyCallbackMode.ASK_FIRST -> "Ask first"
    EmergencyCallbackMode.COUNTDOWN_AUTO_CALL -> "Visible countdown, then call"
}
