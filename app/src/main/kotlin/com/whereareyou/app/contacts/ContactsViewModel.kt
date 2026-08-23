package com.whereareyou.app.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.whereareyou.core.model.TrustedContact
import com.whereareyou.core.model.TrustedContactId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ContactsViewModel(private val repository: TrustedContactRepository) : ViewModel() {

    val contacts: StateFlow<List<TrustedContact>> = repository.contacts

    fun setEnabled(id: TrustedContactId, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun remove(id: TrustedContactId) {
        viewModelScope.launch { repository.remove(id) }
    }

    class Factory(private val repository: TrustedContactRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ContactsViewModel::class.java))
            return ContactsViewModel(repository) as T
        }
    }
}
