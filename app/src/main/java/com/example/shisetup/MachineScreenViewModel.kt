package com.example.shisetup

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MachineScreenViewModel(application: Application) : AndroidViewModel(application) {
    val context by lazy { getApplication<Application>().applicationContext }
    var address = mutableStateOf("192.168.1.8")
    var loading = mutableStateOf(false)

    suspend fun onContinueButtonClicked() = viewModelScope.launch {
        loading.value = true
        loading.value = false
    }
}