package me.weishu.kernelsu.jwsk.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiConversationState(
    val messages: List<AiMessage> = emptyList(),
    val sending: Boolean = false,
    val error: AiError? = null,
)

/** An in-memory text conversation. No persisted transcript or automatic device information. */
class AiConversation(
    private val scope: CoroutineScope,
    private val settings: AiSettingsRepository,
    private val service: AiTextService,
) {
    private val mutableState = MutableStateFlow(AiConversationState())
    val state = mutableState.asStateFlow()
    private var active: Job? = null
    private var profileUsed: AiProfile? = null
    private var generation = 0L

    /** Call from the interface's main dispatcher. Concurrent taps cannot create duplicate requests. */
    fun send(text: String) {
        if (active?.isActive == true || text.isBlank()) return
        if (text.length > 20_000) {
            mutableState.value = mutableState.value.copy(error = AiError.TOO_LARGE)
            return
        }
        val token = generation
        mutableState.value = mutableState.value.copy(sending = true, error = null)
        active = scope.launch {
            try {
                val profile = settings.activeProfile()?.takeIf { it.isConfigured }
                    ?: throw AiFailure(AiError.CONFIGURATION)
                // A provider/configuration change starts a fresh conversation; history is never forwarded silently.
                val previous = if (profileUsed == profile) mutableState.value.messages else emptyList()
                val messages = previous + AiMessage(MessageRole.USER, text.trim())
                if (messages.size > 39 || messages.sumOf { it.text.length.toLong() } > 180_000) {
                    throw AiFailure(AiError.TOO_LARGE)
                }
                val reply = service.chat(profile, messages)
                if (token == generation) {
                    profileUsed = profile
                    mutableState.value = AiConversationState(messages + AiMessage(MessageRole.ASSISTANT, reply))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (token == generation) mutableState.value = mutableState.value.copy(
                    sending = false, error = (failure as? AiFailure)?.error ?: AiError.CONFIGURATION)
            } finally {
                if (token == generation) mutableState.value = mutableState.value.copy(sending = false)
            }
        }
    }

    fun cancel() {
        generation++
        active?.cancel()
        active = null
        mutableState.value = mutableState.value.copy(sending = false)
    }

    fun clear() {
        cancel()
        profileUsed = null
        mutableState.value = AiConversationState()
    }
}
