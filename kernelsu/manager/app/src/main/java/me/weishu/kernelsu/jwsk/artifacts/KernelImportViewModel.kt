package me.weishu.kernelsu.jwsk.artifacts

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class KernelImportState(
    val busy: Boolean = false,
    val name: String? = null,
    val format: String? = null,
    val bytes: Long = 0,
    val sha256: String? = null,
    val failed: Boolean = false,
)

class KernelImportViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = KernelArtifactRepository(app)
    private var accepted: KernelArtifactRepository.AcceptedArtifact? = null
    private val mutableState = MutableStateFlow(KernelImportState())
    val state = mutableState.asStateFlow()

    fun accept(uri: Uri) {
        if (mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy = true, failed = false)
        viewModelScope.launch {
            var incoming: KernelArtifactRepository.AcceptedArtifact? = null
            try {
                val artifact = repository.accept(uri)
                incoming = artifact
                accepted?.let { repository.release(it) }
                accepted = artifact
                incoming = null
                mutableState.value = KernelImportState(name = artifact.displayName,
                    format = artifact.inspection.header().kind().name,
                    bytes = artifact.inspection.bytes(), sha256 = artifact.inspection.sha256())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(busy = false, failed = true)
            } finally {
                incoming?.let { artifact -> withContext(NonCancellable) { repository.release(artifact) } }
            }
        }
    }

    override fun onCleared() {
        // Only the exact private copy owned by this importer; the original document is untouched.
        accepted?.localFile?.delete()
        super.onCleared()
    }
}
