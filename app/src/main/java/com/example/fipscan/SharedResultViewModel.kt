package com.example.fipscan

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedResultViewModel : ViewModel() {
    private val _selectedResult = MutableLiveData<ResultEntity?>()
    val selectedResult: LiveData<ResultEntity?> = _selectedResult

    fun setSelectedResult(result: ResultEntity?) {
        _selectedResult.value = result
    }

    fun clearSelectedResult() {
        _selectedResult.value = null
    }
}