package me.weishu.kernelsu.jwsk.ai

import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

/** Text-only adapters. There is deliberately no tool schema, executor or device context. */
internal object AiProtocol {
    private const val SYSTEM = "You are JWSK's text assistant. Help with chat, Android command suggestions, risk warnings and test plans. " +
        "Use only information supplied in this conversation. Never claim to run commands, inspect or control a device. " +
        "When suggesting commands, explain effects, prerequisites and destructive risks. Clearly distinguish suggestions from completed tests."
    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json; charset=utf-8".toMediaType()

    fun request(profile: AiProfile, history: List<AiMessage>, now: Instant = Instant.now()): Request {
        require(profile.isConfigured) { "Incomplete AI configuration" }
        require(history.isNotEmpty() && history.last().role == MessageRole.USER)
        require(history.size <= 40 && history.sumOf { it.text.length.toLong() } <= 200_000)
        require(history.none { it.text.isBlank() })
        val c = profile.configuration
        val messages = history.dropWhile { it.role != MessageRole.USER }
        var url = baseUrl(c).newBuilder()
        val builder = Request.Builder().header("Accept", "application/json")
        val body = when (c.provider) {
            AiProvider.OPENAI -> {
                url.addPathSegment("responses")
                builder.header("Authorization", "Bearer ${profile.credentials.apiKey}")
                buildJsonObject {
                    put("model", c.model.trim()); put("instructions", SYSTEM); put("store", false); put("stream", false)
                    put("input", wireMessages(messages))
                }
            }
            AiProvider.AZURE_OPENAI -> {
                url.addPathSegment("openai").addPathSegment("deployments").addPathSegment(c.azureDeployment.trim())
                    .addPathSegment("chat").addPathSegment("completions")
                    .addQueryParameter("api-version", c.azureApiVersion.trim())
                builder.header("api-key", profile.credentials.apiKey)
                chatBody(c, messages, includeModel = false)
            }
            AiProvider.ANTHROPIC -> {
                url.addPathSegment("messages")
                builder.header("x-api-key", profile.credentials.apiKey).header("anthropic-version", "2023-06-01")
                buildJsonObject {
                    put("model", c.model.trim()); put("max_tokens", 4096); put("system", SYSTEM)
                    put("messages", wireMessages(messages))
                }
            }
            AiProvider.GEMINI -> {
                url.addPathSegment("models").addPathSegment("${c.model.trim().removePrefix("models/")}:generateContent")
                builder.header("x-goog-api-key", profile.credentials.apiKey)
                buildJsonObject {
                    put("systemInstruction", buildJsonObject { put("parts", textParts(SYSTEM)) })
                    put("contents", buildJsonArray {
                        messages.forEach { message -> add(buildJsonObject {
                            put("role", if (message.role == MessageRole.USER) "user" else "model")
                            put("parts", textParts(message.text))
                        }) }
                    })
                }
            }
            AiProvider.OLLAMA -> {
                url.addPathSegment("api").addPathSegment("chat")
                if (profile.credentials.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${profile.credentials.apiKey}")
                chatBody(c, messages)
            }
            AiProvider.AWS_BEDROCK -> {
                url.addPathSegment("model").addPathSegment(c.model.trim()).addPathSegment("converse")
                if (c.bedrockAuthentication == BedrockAuthentication.BEARER) builder.header("Authorization", "Bearer ${profile.credentials.apiKey}")
                buildJsonObject {
                    put("system", textParts(SYSTEM))
                    put("messages", buildJsonArray {
                        messages.forEach { message -> add(buildJsonObject {
                            put("role", message.role.wireName); put("content", textParts(message.text))
                        }) }
                    })
                    put("inferenceConfig", buildJsonObject { put("maxTokens", 4096) })
                }
            }
            else -> {
                url.addPathSegment("chat").addPathSegment("completions")
                builder.header("Authorization", "Bearer ${profile.credentials.apiKey}")
                chatBody(c, messages)
            }
        }.toString()
        val request = builder.url(url.build()).post(body.toRequestBody(contentType)).build()
        return if (c.provider == AiProvider.AWS_BEDROCK && c.bedrockAuthentication == BedrockAuthentication.AWS_CREDENTIALS) {
            BedrockSigner.sign(request, body.toByteArray(Charsets.UTF_8), c.bedrockRegion.trim(), profile.credentials, now)
        } else request
    }

    fun response(provider: AiProvider, body: String): String {
        val root = json.parseToJsonElement(body).jsonObject
        val text = when (provider) {
            AiProvider.OPENAI -> root.array("output").mapNotNull { it as? JsonObject }
                .filter { it.string("type") == "message" && it.string("role") == "assistant" }
                .flatMap { it.array("content") }.mapNotNull { it as? JsonObject }
                .filter { it.string("type") == "output_text" }.mapNotNull { it.string("text") }.joinToString("\n")
            AiProvider.ANTHROPIC -> root.array("content").mapNotNull { it as? JsonObject }
                .filter { it.string("type") == "text" }.mapNotNull { it.string("text") }.joinToString("\n")
            AiProvider.GEMINI -> root.array("candidates").firstOrNull()?.jsonObject?.get("content")?.jsonObject
                ?.array("parts")?.mapNotNull { it as? JsonObject }?.filter { it["thought"] != JsonPrimitive(true) }
                ?.mapNotNull { it.string("text") }?.joinToString("\n").orEmpty()
            AiProvider.AWS_BEDROCK -> root["output"]?.jsonObject?.get("message")?.jsonObject?.array("content")
                ?.mapNotNull { (it as? JsonObject)?.string("text") }?.joinToString("\n").orEmpty()
            AiProvider.OLLAMA -> root["message"]?.jsonObject?.string("content").orEmpty()
            else -> {
                val content = root.array("choices").firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")
                when (content) {
                    is JsonPrimitive -> if (content.isString) content.content else ""
                    is JsonArray -> content.mapNotNull { it as? JsonObject }.filter { it.string("type") == "text" }
                        .mapNotNull { it.string("text") }.joinToString("\n")
                    else -> ""
                }
            }
        }
        return text.trim().ifEmpty { throw AiFailure(AiError.EMPTY_RESPONSE) }
    }

    private fun baseUrl(c: AiConfiguration): HttpUrl {
        val endpoint = if (c.provider == AiProvider.AWS_BEDROCK && c.endpoint.isBlank()) {
            val suffix = if (c.bedrockRegion.startsWith("cn-")) "amazonaws.com.cn" else "amazonaws.com"
            "https://bedrock-runtime.${c.bedrockRegion}.$suffix"
        } else c.endpoint.trim()
        return (endpoint.trimEnd('/') + "/").toHttpUrl()
    }

    private fun chatBody(c: AiConfiguration, messages: List<AiMessage>, includeModel: Boolean = true) = buildJsonObject {
        if (includeModel) put("model", c.model.trim())
        put("stream", false)
        put("messages", wireMessages(listOf(AiMessage(MessageRole.USER, SYSTEM)) + messages, firstSystem = true))
    }

    private fun wireMessages(messages: List<AiMessage>, firstSystem: Boolean = false) = buildJsonArray {
        messages.forEachIndexed { index, message -> add(buildJsonObject {
            put("role", if (firstSystem && index == 0) "system" else message.role.wireName)
            put("content", message.text)
        }) }
    }

    private fun textParts(text: String) = buildJsonArray { add(buildJsonObject { put("text", text) }) }
    private fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
