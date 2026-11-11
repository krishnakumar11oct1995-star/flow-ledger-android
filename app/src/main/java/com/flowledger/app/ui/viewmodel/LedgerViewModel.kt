package com.flowledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowledger.app.data.entity.TransactionEntity
import com.flowledger.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    val totalBalance: StateFlow<Double> = repository.observeTotalBalance()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    private val monthRangeFlow = MutableStateFlow(currentMonthRange())

    val monthlyInflow: StateFlow<Double> = monthRangeFlow
        .flatMapLatest { (start, end) ->
            repository.observeMonthlyInflow(start, end)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val monthlyOutflow: StateFlow<Double> = monthRangeFlow
        .flatMapLatest { (start, end) ->
            repository.observeMonthlyOutflow(start, end)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    val transactions: StateFlow<List<TransactionEntity>> = repository.observeTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun toggleExcluded(transactionId: String, excluded: Boolean) {
        viewModelScope.launch {
            repository.setExcluded(transactionId, excluded)
        }
    }

    private fun currentMonthRange(): Pair<Long, Long> {
        val zoneId = ZoneId.systemDefault()
        val yearMonth = YearMonth.now(zoneId)
        val startOfMonth = yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endOfMonth = yearMonth.atEndOfMonth().atTime(23, 59, 59, 999999999)
            .atZone(zoneId).toInstant().toEpochMilli()
        return Pair(startOfMonth, endOfMonth)
    }
}
