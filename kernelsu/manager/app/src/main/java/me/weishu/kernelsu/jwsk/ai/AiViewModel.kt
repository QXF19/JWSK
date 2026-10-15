package me.weishu.kernelsu.jwsk.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.weishu.kernelsu.ksuApp

enum class AiSettingsError { READ_FAILED, SAVE_FAILED }

/** Native screen state. Stored credential values are never exposed as form defaults. */
class AiViewModel : ViewModel() {
    private val repository = ksuApp.aiSettings
    val settings = repository.snapshot
    val conversation = AiConversation(viewModelScope, repository, ksuApp.aiTextService)
    private val mutableError = MutableStateFlow<AiSettingsError?>(null)
    val settingsError = mutableError.asStateFlow()
    private val mutableSaving = MutableStateFlow(false)
    val saving = mutableSaving.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            try {
                repository.load()
                mutableError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = AiSettingsError.READ_FAILED
            }
        }
    }

    fun save(configuration: AiConfiguration, credentials: AiCredentialEdit, onSaved: () -> Unit = {}) {
        if (mutableSaving.value) return
        mutableSaving.value = true
        viewModelScope.launch {
            try {
                repository.save(configuration, credentials)
                conversation.clear()
                mutableError.value = null
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = AiSettingsError.SAVE_FAILED
            } finally {
                mutableSaving.value = false
            }
        }
    }

    fun selectProvider(provider: AiProvider) {
        if (mutableSaving.value) return
        mutableSaving.value = true
        viewModelScope.launch {
            try {
                repository.select(provider)
                conversation.clear()
                mutableError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = AiSettingsError.SAVE_FAILED
            } finally {
                mutableSaving.value = false
            }
        }
    }
}
