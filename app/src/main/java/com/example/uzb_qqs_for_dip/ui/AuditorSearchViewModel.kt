package com.example.uzb_qqs_for_dip.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uzb_qqs_for_dip.QqsApp
import com.example.uzb_qqs_for_dip.data.model.ReceiptWithUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuditorSearchViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as QqsApp).container

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<ReceiptWithUser>>(emptyList())
    val results: StateFlow<List<ReceiptWithUser>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(value: String) {
        _query.value = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(value)
        }
    }

    private suspend fun runSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) {
            _results.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        _results.value = container.receiptRepository.search(q)
        _isSearching.value = false
    }
}
