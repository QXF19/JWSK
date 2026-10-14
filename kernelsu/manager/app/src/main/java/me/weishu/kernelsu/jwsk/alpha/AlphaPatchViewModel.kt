package me.weishu.kernelsu.jwsk.alpha

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.jwsk.artifacts.KernelArtifactRepository

internal data class AlphaPatchState(
    val busy: Boolean = false, val name: String? = null, val sha256: String? = null,
    val ready: Boolean = false, val saved: Boolean = false, val error: Boolean = false,
    val log: String = "", val options: AlphaPatchOptions = AlphaPatchOptions(),
)

internal class AlphaPatchViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = KernelArtifactRepository(app, bootImageOnly = true)
    private val patcher = AlphaBootPatcher(app)
    private var input: KernelArtifactRepository.AcceptedArtifact? = null
    private var output: AlphaPatchOutput? = null
    private var operation: Job? = null
    private val mutable = MutableStateFlow(AlphaPatchState())
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            try { awaitCancellation() } finally {
                withContext(NonCancellable) {
                    operation?.join()
                    output?.let { patcher.release(it) }
                    input?.let { repository.release(it) }
                }
            }
        }
    }

    fun options(value: AlphaPatchOptions) {
        if (!mutable.value.busy && !mutable.value.ready) mutable.value = mutable.value.copy(options = value)
    }

    fun select(uri: Uri) = launchOperation {
        val incoming = repository.accept(uri)
        val previous = input
        input = incoming
        withContext(NonCancellable) {
            previous?.let { repository.release(it) }
            output?.let { patcher.release(it) }
            output = null
        }
        mutable.value = AlphaPatchState(name = incoming.displayName, sha256 = incoming.inspection.sha256(),
            options = mutable.value.options, busy = true)
    }

    fun patch() = launchOperation {
        val source = input ?: error("Select an image first")
        output?.let { patcher.release(it) }
        output = null
        mutable.value = mutable.value.copy(ready = false, saved = false, log = "")
        val patched = patcher.patch(source.localFile, mutable.value.options)
        output = patched
        mutable.value = mutable.value.copy(ready = true, sha256 = patched.inspection.sha256(), log = patched.log)
    }

    fun export(uri: Uri) = launchOperation {
        val image = output?.image ?: error("No patched image")
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()
            getApplication<Application>().contentResolver.openOutputStream(uri, "w")?.use { destination ->
                image.inputStream().use { source ->
                    val buffer = ByteArray(65536)
                    while (true) {
                        job.ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        destination.write(buffer, 0, count)
                    }
                }
            } ?: error("Cannot open destination")
        }
        mutable.value = mutable.value.copy(saved = true)
    }

    fun cancel() { operation?.cancel() }

    private fun launchOperation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = false, saved = false)
        operation = viewModelScope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                mutable.value = mutable.value.copy(error = true,
                    log = (failure as? AlphaPatchFailure)?.diagnosticLog ?: mutable.value.log)
            } finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
}
