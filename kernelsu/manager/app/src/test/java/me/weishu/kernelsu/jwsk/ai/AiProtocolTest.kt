package me.weishu.kernelsu.jwsk.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AiProtocolTest {
    private val history = listOf(AiMessage(MessageRole.USER, "How can I inspect a boot image?"))

    @Test fun everyProviderUsesItsOwnTextProtocol() {
        val expected = mapOf(
            AiProvider.AZURE_OPENAI to "/openai/deployments/deployment/chat/completions",
            AiProvider.OLLAMA to "/api/chat",
            AiProvider.OPENAI to "/v1/responses",
            AiProvider.ANTHROPIC to "/v1/messages",
            AiProvider.GEMINI to "/v1beta/models/model:generateContent",
            AiProvider.AWS_BEDROCK to "/model/model/converse",
            AiProvider.DEEPSEEK to "/chat/completions",
            AiProvider.GLM to "/api/paas/v4/chat/completions",
            AiProvider.KIMI to "/v1/chat/completions",
            AiProvider.QWEN to "/compatible-mode/v1/chat/completions",
            AiProvider.MINIMAX to "/v1/chat/completions",
            AiProvider.MISTRAL to "/v1/chat/completions",
            AiProvider.XAI to "/v1/chat/completions",
        )
        assertEquals(AiProvider.entries.size, expected.size)
        for ((provider, path) in expected) {
            val request = AiProtocol.request(profile(provider), history, Instant.parse("2025-01-01T00:00:00Z"))
            assertEquals(provider.name, path, request.url.encodedPath)
            assertEquals("POST", request.method)
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            val body = buffer.readUtf8()
            assertFalse(provider.name, body.contains("secret-value"))
            assertFalse(provider.name, body.contains("tools\""))
            assertNotNull(Json.parseToJsonElement(body).jsonObject)
        }
    }

    @Test fun toolCallsAreNeverAcceptedAsAssistantText() {
        assertEquals("Safe guidance", AiProtocol.response(AiProvider.OPENAI,
            """{"output":[{"type":"function_call","name":"run"},{"type":"message","role":"assistant","content":[{"type":"output_text","text":"Safe guidance"}]}]}"""))
        assertEquals("Advice", AiProtocol.response(AiProvider.ANTHROPIC,
            """{"content":[{"type":"tool_use","name":"shell"},{"type":"text","text":"Advice"}]}"""))
        assertEquals("Visible", AiProtocol.response(AiProvider.GEMINI,
            """{"candidates":[{"content":{"parts":[{"thought":true,"text":"hidden"},{"text":"Visible"}]}}]}"""))
    }

    @Test fun missingCredentialsDoNotCountAsConfigured() {
        for (provider in AiProvider.entries) {
            val empty = profile(provider).copy(credentials = AiCredentials())
            assertEquals(provider == AiProvider.OLLAMA, empty.isConfigured)
        }
        assertTrue(AiSettings().showUnconfiguredBadge)
        assertFalse(AiSettings().withProfile(profile(AiProvider.OPENAI)).showUnconfiguredBadge)
        assertFalse(profile(AiProvider.OPENAI).toString().contains("secret-value"))
    }

    @Test fun httpIsLimitedToLocalOllama() {
        assertTrue(allowedEndpoint(AiProvider.OLLAMA, "http://127.0.0.1:11434"))
        assertFalse(allowedEndpoint(AiProvider.OPENAI, "http://127.0.0.1:11434"))
        assertFalse(allowedEndpoint(AiProvider.OLLAMA, "http://example.com"))
        assertFalse(allowedEndpoint(AiProvider.OLLAMA, "http://192.168.1.2:11434"))
        assertFalse(allowedEndpoint(AiProvider.OPENAI, "https://user:password@example.com"))
    }

    private fun profile(provider: AiProvider): AiProfile {
        val configuration = AiConfiguration(provider,
            endpoint = if (provider == AiProvider.AZURE_OPENAI) "https://resource.openai.azure.com" else provider.defaultEndpoint,
            model = "model", azureDeployment = "deployment", bedrockRegion = "us-east-1")
        return AiProfile(configuration, AiCredentials(apiKey = "secret-value", accessKey = "access", secretKey = "secret"))
    }
}
