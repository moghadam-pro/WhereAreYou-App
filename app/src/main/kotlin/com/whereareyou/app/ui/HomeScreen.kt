package com.whereareyou.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whereareyou.app.contacts.ContactsViewModel
import com.whereareyou.app.contacts.TrustedContactRepository
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
    val viewModel: ContactsViewModel = viewModel(factory = ContactsViewModel.Factory(repository))
    val contacts by viewModel.contacts.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("WhereAreYou") }) },
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
            item { ReadinessBanner(onOpenPermissions) }
            item { SafetySessionPlaceholder() }
            item {
                Text(
                    "Trusted contacts",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (contacts.isEmpty()) {
                item { Text("No trusted contacts yet. Add one to get started.", style = MaterialTheme.typography.bodyMedium) }
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
private fun ReadinessBanner(onOpenPermissions: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("App readiness", style = MaterialTheme.typography.titleMedium)
                Text(
                    "No dangerous permissions requested yet (Phase 1A/1B build).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onOpenPermissions) {
                Icon(Icons.Default.Settings, contentDescription = "Permissions & readiness")
            }
        }
    }
}

@Composable
private fun SafetySessionPlaceholder() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Safety session", style = MaterialTheme.typography.titleMedium)
            Text(
                "No active safety session. Triggers are not wired to real events yet in this build.",
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
