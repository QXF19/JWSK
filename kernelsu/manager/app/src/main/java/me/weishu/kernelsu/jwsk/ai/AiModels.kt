package me.weishu.kernelsu.jwsk.ai

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
enum class AiProvider(val displayName: String, val defaultEndpoint: String) {
    AZURE_OPENAI("Azure OpenAI", ""),
    OLLAMA("Ollama", "http://127.0.0.1:11434"),
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1"),
    GEMINI("Google AI (Gemini)", "https://generativelanguage.googleapis.com/v1beta"),
    AWS_BEDROCK("AWS Bedrock", ""),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com"),
    GLM("GLM", "https://open.bigmodel.cn/api/paas/v4"),
    KIMI("Kimi", "https://api.moonshot.cn/v1"),
    QWEN("Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    MINIMAX("MiniMax", "https://api.minimax.io/v1"),
    MISTRAL("Mistral", "https://api.mistral.ai/v1"),
    XAI("xAI (Grok)", "https://api.x.ai/v1"),
}

@Serializable
enum class BedrockAuthentication { BEARER, AWS_CREDENTIALS }

@Serializable
data class AiConfiguration(
    val provider: AiProvider,
    val endpoint: String = provider.defaultEndpoint,
    val model: String = "",
    val azureDeployment: String = "",
    val azureApiVersion: String = "2024-10-21",
    val bedrockRegion: String = "",
    val bedrockAuthentication: BedrockAuthentication = BedrockAuthentication.BEARER,
)

/** Serializable only inside the encrypted store. Never log this object or an HTTP request. */
@Serializable
data class AiCredentials(
    val apiKey: String = "",
    val accessKey: String = "",
    val secretKey: String = "",
    val sessionToken: String = "",
) {
    override fun toString(): String = "AiCredentials(<redacted>)"
}

@Serializable
data class AiProfile(val configuration: AiConfiguration, val credentials: AiCredentials = AiCredentials()) {
    val isConfigured: Boolean get() = missingFields().isEmpty()

    fun missingFields(): Set<String> = buildSet {
        val c = configuration
        if (c.provider == AiProvider.AWS_BEDROCK) {
            if (!c.bedrockRegion.matches(Regex("[a-z]{2}(?:-[a-z]+)+-[0-9]+"))) add("region")
            if (c.endpoint.isNotBlank() && !allowedEndpoint(c.provider, c.endpoint)) add("endpoint")
        } else if (!allowedEndpoint(c.provider, c.endpoint)) add("endpoint")
        if (c.provider == AiProvider.AZURE_OPENAI) {
            if (c.azureDeployment.isBlank()) add("deployment")
            if (c.azureApiVersion.isBlank()) add("apiVersion")
        } else if (c.model.isBlank()) add("model")
        when (c.provider) {
            AiProvider.OLLAMA -> Unit
            AiProvider.AWS_BEDROCK -> if (c.bedrockAuthentication == BedrockAuthentication.BEARER) {
                if (credentials.apiKey.isBlank()) add("bearerToken")
            } else {
                if (credentials.accessKey.isBlank()) add("accessKey")
                if (credentials.secretKey.isBlank()) add("secretKey")
            }
            else -> if (credentials.apiKey.isBlank()) add("apiKey")
        }
        // Reject control characters in credentials before constructing authentication headers.
        if (listOf(credentials.apiKey, credentials.accessKey, credentials.secretKey, credentials.sessionToken)
                .any { value -> value.any { it.isISOControl() } }) add("credentials")
    }
}

internal fun allowedEndpoint(provider: AiProvider, endpoint: String): Boolean = runCatching {
    val uri = URI(endpoint.trim())
    val host = uri.host ?: return@runCatching false
    if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
        (uri.port != -1 && uri.port !in 1..65535)) return@runCatching false
    if (uri.scheme.equals("https", true)) return@runCatching true
    uri.scheme.equals("http", true) && provider == AiProvider.OLLAMA && isLocalLiteral(host)
}.getOrDefault(false)

private fun isLocalLiteral(host: String): Boolean {
    if (host.equals("localhost", true) || host == "[::1]" || host == "::1") return true
    // Android's network-security policy permits cleartext only on loopback.
    // Remote/LAN services use HTTPS; accepting an address that the OS rejects is misleading.
    return host == "127.0.0.1"
}

@Serializable
enum class MessageRole(val wireName: String) { USER("user"), ASSISTANT("assistant") }

@Serializable
data class AiMessage(val role: MessageRole, val text: String)

@Serializable
data class AiSettings(
    val activeProvider: AiProvider = AiProvider.OPENAI,
    val profiles: List<AiProfile> = emptyList(),
) {
    val activeProfile: AiProfile? get() = profiles.firstOrNull { it.configuration.provider == activeProvider }
    val showUnconfiguredBadge: Boolean get() = activeProfile?.isConfigured != true

    fun withProfile(profile: AiProfile): AiSettings = copy(
        activeProvider = profile.configuration.provider,
        profiles = profiles.filterNot { it.configuration.provider == profile.configuration.provider } + profile,
    )
}
