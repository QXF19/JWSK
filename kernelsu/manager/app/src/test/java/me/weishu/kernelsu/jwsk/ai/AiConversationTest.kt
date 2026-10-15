package me.weishu.kernelsu.jwsk.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class AiConversationTest {
    @Test fun providerSwitchStartsFreshAndUsesOnlySelectedCredential() = runBlocking {
        val repository = repository()
        val captured = mutableListOf<Pair<AiProfile, List<AiMessage>>>()
        val service = object : AiTextService {
            override suspend fun chat(profile: AiProfile, messages: List<AiMessage>): String {
                captured += profile to messages
                return "Advice"
            }
        }
        val conversation = AiConversation(this, repository, service)
        conversation.send("First provider")
        yield()
        repository.save(AiConfiguration(AiProvider.ANTHROPIC, model = "model"), AiCredentialEdit(apiKey = "second"))
        conversation.send("Second provider")
        yield()
        assertEquals(2, captured.size)
        assertEquals(listOf(AiMessage(MessageRole.USER, "Second provider")), captured.last().second)
        assertEquals("second", captured.last().first.credentials.apiKey)
    }

    @Test fun clearCancelsPendingRequestAndDiscardsItsResult() = runBlocking {
        val reply = CompletableDeferred<String>()
        var requests = 0
        val service = object : AiTextService {
            override suspend fun chat(profile: AiProfile, messages: List<AiMessage>): String {
                requests++
                return reply.await()
            }
        }
        val conversation = AiConversation(this, repository(), service)
        conversation.send("Question")
        conversation.send("Duplicate tap")
        yield()
        assertEquals(1, requests)
        conversation.clear()
        reply.complete("Late reply")
        yield()
        assertTrue(conversation.state.value.messages.isEmpty())
        assertFalse(conversation.state.value.sending)
    }

    private suspend fun repository(): AiSettingsRepository {
        val store = object : AiProfileStore {
            var value = AiSettings()
            override suspend fun load() = value
            override suspend fun save(settings: AiSettings) { value = settings }
        }
        return AiSettingsRepository(store).apply {
            save(AiConfiguration(AiProvider.OPENAI, model = "model"), AiCredentialEdit(apiKey = "first"))
        }
    }
}
