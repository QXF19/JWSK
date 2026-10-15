package me.weishu.kernelsu.jwsk.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AiSettingsRepositoryTest {
    @Test fun failedSaveKeepsPreviousConfigurationAndBadge() = runBlocking {
        val store = MemoryStore()
        val repository = AiSettingsRepository(store)
        repository.load()
        assertTrue(repository.snapshot.value.showUnconfiguredBadge)
        store.failWrites = true
        try {
            repository.save(config(AiProvider.OPENAI), AiCredentialEdit(apiKey = "private-key"))
            fail("Save must fail")
        } catch (_: IOException) { }
        assertTrue(repository.snapshot.value.showUnconfiguredBadge)
        assertTrue(repository.snapshot.value.profiles.isEmpty())
    }

    @Test fun savingAndEditingPreservesKeysWithoutExposingThemToState() = runBlocking {
        val store = MemoryStore()
        val repository = AiSettingsRepository(store)
        repository.save(config(AiProvider.OPENAI), AiCredentialEdit(apiKey = "private-key"))
        assertFalse(repository.snapshot.value.showUnconfiguredBadge)
        assertFalse(repository.snapshot.value.toString().contains("private-key"))
        repository.save(config(AiProvider.OPENAI).copy(model = "another-model"), AiCredentialEdit())
        assertEquals("private-key", store.value.activeProfile!!.credentials.apiKey)
        repository.save(config(AiProvider.OPENAI), AiCredentialEdit(apiKey = ""))
        assertTrue(repository.snapshot.value.showUnconfiguredBadge)
    }

    @Test fun switchingProvidersNeverCopiesCredentialsAcrossProfiles() = runBlocking {
        val store = MemoryStore()
        val repository = AiSettingsRepository(store)
        repository.save(config(AiProvider.OPENAI), AiCredentialEdit(apiKey = "openai-only"))
        repository.save(config(AiProvider.ANTHROPIC), AiCredentialEdit())
        assertTrue(repository.snapshot.value.showUnconfiguredBadge)
        assertEquals("", store.value.activeProfile!!.credentials.apiKey)
        repository.select(AiProvider.OPENAI)
        assertFalse(repository.snapshot.value.showUnconfiguredBadge)
    }

    @Test fun unreadableEncryptedSettingsAreNotSilentlyOverwritten() = runBlocking {
        var writes = 0
        val repository = AiSettingsRepository(object : AiProfileStore {
            override suspend fun load(): AiSettings = throw IOException("Unreadable")
            override suspend fun save(settings: AiSettings) { writes++ }
        })
        try {
            repository.save(config(AiProvider.OPENAI), AiCredentialEdit(apiKey = "replacement"))
            fail("Unreadable settings must be surfaced")
        } catch (_: IOException) { }
        assertEquals(0, writes)
        assertFalse(repository.snapshot.value.loaded)
    }

    private fun config(provider: AiProvider) = AiConfiguration(provider, model = "model")
    private class MemoryStore : AiProfileStore {
        var value = AiSettings()
        var failWrites = false
        override suspend fun load() = value
        override suspend fun save(settings: AiSettings) {
            if (failWrites) throw IOException("Storage failure")
            value = settings
        }
    }
}
