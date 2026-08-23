package com.whereareyou.app.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whereareyou.core.model.ContactCapabilities
import com.whereareyou.core.model.EmergencyCallbackMode
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Form state for adding or editing one [TrustedContact] (PRODUCT_SPEC.md section 5). */
data class ContactFormState(
    val editingId: TrustedContactId? = null,
    val displayName: String = "",
    val phoneNumber: String = "",
    val enabled: Boolean = true,
    val missedCallTrigger: Boolean = true,
    val smsCommand: Boolean = true,
    val receiveLowBatteryAlert: Boolean = false,
    val manualStatusRecipient: Boolean = true,
    val emergencyCallback: EmergencyCallbackMode = EmergencyCallbackMode.OFF,
    val commandSecret: String = "",
    val phoneError: String? = null,
    val saved: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null
}

class ContactEditViewModel(
    private val repository: TrustedContactRepository,
    contactIdToEdit: TrustedContactId?,
) : ViewModel() {

    private val _state = MutableStateFlow(ContactFormState(editingId = contactIdToEdit))
    val state: StateFlow<ContactFormState> = _state.asStateFlow()

    init {
        val existing = contactIdToEdit?.let { id -> repository.contactLookup.findById(id) }
        if (existing != null) {
            _state.value = existing.toFormState()
        }
    }

    fun onDisplayNameChanged(value: String) {
        _state.value = _state.value.copy(displayName = value)
    }

    fun onPhoneNumberChanged(value: String) {
        _state.value = _state.value.copy(phoneNumber = value, phoneError = null)
    }

    fun onCommandSecretChanged(value: String) {
        _state.value = _state.value.copy(commandSecret = value)
    }

    fun onEnabledChanged(value: Boolean) {
        _state.value = _state.value.copy(enabled = value)
    }

    fun onMissedCallTriggerChanged(value: Boolean) {
        _state.value = _state.value.copy(missedCallTrigger = value)
    }

    fun onSmsCommandChanged(value: Boolean) {
        _state.value = _state.value.copy(smsCommand = value)
    }

    fun onReceiveLowBatteryAlertChanged(value: Boolean) {
        _state.value = _state.value.copy(receiveLowBatteryAlert = value)
    }

    fun onManualStatusRecipientChanged(value: Boolean) {
        _state.value = _state.value.copy(manualStatusRecipient = value)
    }

    fun onEmergencyCallbackChanged(value: EmergencyCallbackMode) {
        _state.value = _state.value.copy(emergencyCallback = value)
    }

    fun save() {
        val form = _state.value
        when (val result = repository.validate(form.phoneNumber)) {
            is ContactInputResult.Invalid -> {
                _state.value = form.copy(phoneError = result.reason)
                return
            }
            is ContactInputResult.Valid -> viewModelScope.launch {
                val contact = TrustedContact(
                    id = form.editingId ?: TrustedContactId(""),
                    displayName = form.displayName.trim(),
                    canonicalPhoneNumber = result.canonicalPhoneNumber,
                    displayPhoneNumber = result.displayPhoneNumber,
                    enabled = form.enabled,
                    capabilities = ContactCapabilities(
                        missedCallTrigger = form.missedCallTrigger,
                        smsCommand = form.smsCommand,
                        receiveLowBatteryAlert = form.receiveLowBatteryAlert,
                        manualStatusRecipient = form.manualStatusRecipient,
                        emergencyCallback = form.emergencyCallback,
                    ),
                    commandSecret = form.commandSecret.trim().ifBlank { null },
                )
                if (form.isEditing) repository.update(contact) else repository.add(contact)
                _state.value = _state.value.copy(saved = true)
            }
        }
    }

    fun delete() {
        val id = _state.value.editingId ?: return
        viewModelScope.launch {
            repository.remove(id)
            _state.value = _state.value.copy(saved = true)
        }
    }

    private fun TrustedContact.toFormState() = ContactFormState(
        editingId = id,
        displayName = displayName,
        phoneNumber = displayPhoneNumber,
        enabled = enabled,
        missedCallTrigger = capabilities.missedCallTrigger,
        smsCommand = capabilities.smsCommand,
        receiveLowBatteryAlert = capabilities.receiveLowBatteryAlert,
        manualStatusRecipient = capabilities.manualStatusRecipient,
        emergencyCallback = capabilities.emergencyCallback,
        commandSecret = commandSecret.orEmpty(),
    )

    class Factory(
        private val repository: TrustedContactRepository,
        private val contactIdToEdit: TrustedContactId?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ContactEditViewModel::class.java))
            return ContactEditViewModel(repository, contactIdToEdit) as T
        }
    }
}
