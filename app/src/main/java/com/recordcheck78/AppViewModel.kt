package com.recordcheck78

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel that manages the app state: scanning, checking, and donation list.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val ocrService = OcrService()
    private val archiveService = InternetArchiveService()
    private val repository = DonationRepository(application)

    // ─── State ──────────────────────────────────────────

    sealed class UiState {
        object Idle : UiState()
        object Scanning : UiState()           // OCR + labeling in progress
        object CheckingArchive : UiState()    // IA lookup in progress
        data class Result(val checkResult: ArchiveCheckResult) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Batch queue
    private val _batchQueue = MutableStateFlow<List<ArchiveCheckResult>>(emptyList())
    val batchQueue: StateFlow<List<ArchiveCheckResult>> = _batchQueue.asStateFlow()

    // Donation list
    private val _donationList = MutableStateFlow<List<DonationListItem>>(emptyList())
    val donationList: StateFlow<List<DonationListItem>> = _donationList.asStateFlow()

    // Current scanned record (before IA check)
    private val _currentRecord = MutableStateFlow<Record?>(null)
    val currentRecord: StateFlow<Record?> = _currentRecord.asStateFlow()

    init {
        loadDonationList()
    }

    /**
     * Process a photo: run OCR + image labeling, then check IA.
     */
    fun processPhoto(photoPath: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Scanning
            try {
                val record = ocrService.identifyRecord(photoPath)
                _currentRecord.value = record

                _uiState.value = UiState.CheckingArchive
                val result = archiveService.checkRecord(record)
                _uiState.value = UiState.Result(result)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Add current result to the batch queue for later review.
     */
    fun addToBatch() {
        val state = _uiState.value
        if (state is UiState.Result) {
            _batchQueue.value = _batchQueue.value + state.checkResult
        }
        _uiState.value = UiState.Idle
    }

    /**
     * Save all batch items to the donation list.
     */
    fun commitBatch() {
        viewModelScope.launch {
            _batchQueue.value.forEach { result ->
                repository.add(result.record, result)
            }
            _batchQueue.value = emptyList()
            loadDonationList()
        }
    }

    /**
     * Save a single result to the donation list.
     */
    fun saveToDonationList() {
        val state = _uiState.value
        val record = _currentRecord.value
        if (state is UiState.Result && record != null) {
            viewModelScope.launch {
                repository.add(record, state.checkResult)
                loadDonationList()
                _uiState.value = UiState.Idle
            }
        }
    }

    /**
     * Discard current scan and start over.
     */
    fun reset() {
        _uiState.value = UiState.Idle
        _currentRecord.value = null
    }

    /**
     * Update the status of a donation list item.
     */
    fun updateStatus(id: Long, status: DonationStatus) {
        viewModelScope.launch {
            repository.updateStatus(id, status)
            loadDonationList()
        }
    }

    /**
     * Remove an item from the donation list.
     */
    fun removeItem(id: Long) {
        viewModelScope.launch {
            repository.remove(id)
            loadDonationList()
        }
    }

    /**
     * Manually edit a record's fields and re-check IA.
     */
    fun editAndRecheck(catalogNumber: String, artist: String, title: String, labelName: String) {
        val record = _currentRecord.value ?: return
        val edited = record.copy(
            catalogNumber = catalogNumber.trim(),
            artist = artist.trim(),
            title = title.trim(),
            labelName = labelName.trim()
        )
        _currentRecord.value = edited
        viewModelScope.launch {
            _uiState.value = UiState.CheckingArchive
            try {
                val result = archiveService.checkRecord(edited)
                _uiState.value = UiState.Result(result)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Archive check failed")
            }
        }
    }

    private fun loadDonationList() {
        _donationList.value = repository.getAll()
    }
}