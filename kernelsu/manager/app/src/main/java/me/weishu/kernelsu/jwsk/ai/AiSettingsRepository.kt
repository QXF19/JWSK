package me.weishu.kernelsu.jwsk.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only presence flags, never stored credentials, are published to interface state. */
data class AiProfileSummary(
    val configuration: AiConfiguration,
    val isConfigured: Boolean,
    val hasApiKey: Boolean,
    val hasAccessKey: Boolean,
    val hasSecretKey: Boolean,
    val hasSessionToken: Boolean,
)

data class AiSettingsSnapshot(
    val loaded: Boolean = false,
    val activeProvider: AiProvider = AiProvider.OPENAI,
    val profiles: List<AiProfileSummary> = emptyList(),
) {
    val activeProfile get() = profiles.firstOrNull { it.configuration.provider == activeProvider }
    val showUnconfiguredBadge get() = loaded && activeProfile?.isConfigured != true
}

/** null preserves a stored field; an explicit empty value removes it. */
class AiCredentialEdit(
    val apiKey: String? = null,
    val accessKey: String? = null,
    val secretKey: String? = null,
    val sessionToken: String? = null,
) {
    override fun toString() = "AiCredentialEdit(<redacted>)"
}

class AiSettingsRepository(private val store: AiProfileStore) {
    private val mutex = Mutex()
    private var settings: AiSettings? = null
    private val mutableSnapshot = MutableStateFlow(AiSettingsSnapshot())
    val snapshot = mutableSnapshot.asStateFlow()

    suspend fun load() {
        mutex.withLock { ensureLoaded() }
    }

    suspend fun save(configuration: AiConfiguration, edit: AiCredentialEdit) = mutex.withLock {
        val current = ensureLoaded()
        val previous = current.profiles.firstOrNull { it.configuration.provider == configuration.provider }
            ?.credentials ?: AiCredentials()
        val credentials = AiCredentials(
            edit.apiKey ?: previous.apiKey, edit.accessKey ?: previous.accessKey,
            edit.secretKey ?: previous.secretKey, edit.sessionToken ?: previous.sessionToken,
        )
        val next = current.withProfile(AiProfile(configuration, credentials))
        // Publish only after durable encrypted storage succeeds. A failed write keeps old state.
        store.save(next)
        settings = next
        publish(next)
    }

    suspend fun select(provider: AiProvider) = mutex.withLock {
        val next = ensureLoaded().copy(activeProvider = provider)
        store.save(next)
        settings = next
        publish(next)
    }

    internal suspend fun activeProfile(): AiProfile? = mutex.withLock { ensureLoaded().activeProfile }

    private suspend fun ensureLoaded(): AiSettings {
        settings?.let { return it }
        // Decryption/read errors propagate; they must not silently replace saved credentials.
        return store.load().also { settings = it; publish(it) }
    }

    private fun publish(value: AiSettings) {
        mutableSnapshot.value = AiSettingsSnapshot(true, value.activeProvider, value.profiles.map {
            AiProfileSummary(it.configuration, it.isConfigured,
                it.credentials.apiKey.isNotBlank(), it.credentials.accessKey.isNotBlank(),
                it.credentials.secretKey.isNotBlank(), it.credentials.sessionToken.isNotBlank())
        })
    }
}
