package com.whereareyou.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereareyou.app.R
import com.whereareyou.app.contacts.ContactsViewModel
import com.whereareyou.app.contacts.TrustedContactRepository
import com.whereareyou.app.permissions.PermissionReadiness
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: TrustedContactRepository,
    onAddContact: () -> Unit,
    onEditContact: (TrustedContactId) -> Unit,
    onOpenPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory(repository))
    val contacts by viewModel.contacts.collectAsState()

    var hasMissingCore by remember {
        mutableStateOf(PermissionReadiness.hasMissingCorePermissions(context))
    }

    fun refreshPermissions() {
        hasMissingCore = PermissionReadiness.hasMissingCorePermissions(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissions()
    }

    // Automatically prompt for core permissions on launch if any are missing
    LaunchedEffect(Unit) {
        if (hasMissingCore) {
            val missing = PermissionReadiness.CORE_PERMISSIONS.filter {
                !PermissionReadiness.isPermissionGranted(context, it)
            }
            if (missing.isNotEmpty()) {
                permissionsLauncher.launch(missing.toTypedArray())
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.Add, contentDescription = "Add trusted contact")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ReadinessBanner(
                    hasMissingCore = hasMissingCore,
                    onOpenPermissions = onOpenPermissions,
                    onRequestPermissions = {
                        val missing = PermissionReadiness.CORE_PERMISSIONS.filter {
                            !PermissionReadiness.isPermissionGranted(context, it)
                        }
                        if (missing.isNotEmpty()) {
                            permissionsLauncher.launch(missing.toTypedArray())
                        }
                    },
                )
            }
            item { SafetySessionPlaceholder() }
            item {
                Text(
                    stringResource(R.string.trusted_contacts_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (contacts.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_contacts),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(contacts, key = { it.id.value }) { contact ->
                TrustedContactRow(
                    contact = contact,
                    onToggleEnabled = { enabled -> viewModel.setEnabled(contact.id, enabled) },
                    onClick = { onEditContact(contact.id) },
                )
            }
        }
    }
}

@Composable
private fun ReadinessBanner(
    hasMissingCore: Boolean,
    onOpenPermissions: () -> Unit,
    onRequestPermissions: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPermissions),
        colors = CardDefaults.cardColors(
            containerColor = if (hasMissingCore) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (hasMissingCore) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (hasMissingCore) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(
                            text = stringResource(
                                if (hasMissingCore) R.string.readiness_missing_title else R.string.readiness_ready_title,
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                if (hasMissingCore) R.string.readiness_missing_desc else R.string.readiness_ready_desc,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                IconButton(onClick = onOpenPermissions) {
                    Icon(Icons.Default.Settings, contentDescription = "Permissions & readiness")
                }
            }

            if (hasMissingCore) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.permissions_grant_core))
                }
            }
        }
    }
}

@Composable
private fun SafetySessionPlaceholder() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.safety_session_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.safety_session_idle),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrustedContactRow(
    contact: TrustedContact,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
            ) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                Text(contact.displayPhoneNumber, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = contact.enabled, onCheckedChange = onToggleEnabled)
        }
    }
}
