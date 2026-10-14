package me.weishu.kernelsu.jwsk.artifacts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Owns only app-private copies, never the selected document or any boot partition. */
internal class KernelArtifactRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.applicationContext.cacheDir, "jwsk-kernel-artifacts")

    data class AcceptedArtifact(
        val displayName: String,
        val inspection: KernelArtifactInspector.Inspection,
        internal val localFile: File,
    )

    /** Copies immutable input bytes for inspection; no compilation, patching or flashing. */
    suspend fun accept(uri: Uri): AcceptedArtifact = withContext(Dispatchers.IO) {
        val (displayName, reportedSize) = readMetadata(uri)
        if (reportedSize > KernelArtifactInspector.MAX_BYTES) {
            throw KernelArtifactInspector.InvalidArtifactException(KernelArtifactInspector.Problem.TOO_LARGE)
        }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Artifact cache unavailable")
        val localFile = File.createTempFile("artifact-", ".bin", directory)
        try {
            val coroutineContext = currentCoroutineContext()
            resolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytes = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        bytes += count
                        if (bytes > KernelArtifactInspector.MAX_BYTES) {
                            throw KernelArtifactInspector.InvalidArtifactException(KernelArtifactInspector.Problem.TOO_LARGE)
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Selected document cannot be read")
            coroutineContext.ensureActive()
            val inspection = runInterruptible { KernelArtifactInspector.inspect(localFile) }
            AcceptedArtifact(displayName, inspection, localFile)
        } catch (failure: Throwable) {
            localFile.delete() // Exact file created by this operation; never delete the user's URI.
            throw failure
        }
    }

    suspend fun release(artifact: AcceptedArtifact) = withContext(Dispatchers.IO) {
        val file = artifact.localFile
        if (file.parentFile?.canonicalFile == directory.canonicalFile && file.name.startsWith("artifact-")) {
            file.delete()
        }
        Unit
    }

    private fun readMetadata(uri: Uri): Pair<String, Long> {
        var name = "kernel-artifact"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) {
                    // Display-only. Never use provider-supplied names to construct filesystem paths.
                    name = cursor.getString(nameColumn).take(160).filterNot { it.isISOControl() }
                        .ifBlank { "kernel-artifact" }
                }
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
            }
        }
        return name to size
    }
}
