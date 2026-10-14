package me.weishu.kernelsu.jwsk.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.weishu.kernelsu.R
import me.weishu.kernelsu.jwsk.ai.*
import me.weishu.kernelsu.ui.navigation3.LocalNavigator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun AiHeader(title: String) {
    val navigator = LocalNavigator.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(text = stringResource(R.string.jwsk_back), onClick = { navigator.pop() })
        Text(title, fontSize = 22.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ApiField(label: String, value: String, secret: Boolean = false, onChange: (String) -> Unit) {
    TextField(value = value, onValueChange = onChange, label = label, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false,
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text))
}

@Composable
fun AiSettingsScreen() {
    val model = viewModel<AiViewModel>()
    val snapshot by model.settings.collectAsStateWithLifecycle()
    val saving by model.saving.collectAsStateWithLifecycle()
    val error by model.settingsError.collectAsStateWithLifecycle()
    var provider by remember { mutableStateOf(AiProvider.OPENAI) }
    LaunchedEffect(snapshot.loaded) { if (snapshot.loaded) provider = snapshot.activeProvider }
    val summary = snapshot.profiles.firstOrNull { it.configuration.provider == provider }
    val saved = summary?.configuration ?: AiConfiguration(provider)
    var endpoint by remember(provider, saved) { mutableStateOf(saved.endpoint) }
    var name by remember(provider, saved) { mutableStateOf(saved.model) }
    var deployment by remember(provider, saved) { mutableStateOf(saved.azureDeployment) }
    var apiVersion by remember(provider, saved) { mutableStateOf(saved.azureApiVersion) }
    var region by remember(provider, saved) { mutableStateOf(saved.bedrockRegion) }
    var auth by remember(provider, saved) { mutableStateOf(saved.bedrockAuthentication) }
    // Secrets deliberately use neither rememberSaveable nor defaults from storage.
    var apiKey by remember(provider) { mutableStateOf("") }
    var accessKey by remember(provider) { mutableStateOf("") }
    var secretKey by remember(provider) { mutableStateOf("") }
    var sessionToken by remember(provider) { mutableStateOf("") }
    val configuration = AiConfiguration(provider, endpoint.trim(), name.trim(), deployment.trim(), apiVersion.trim(), region.trim(), auth)
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).imePadding().navigationBarsPadding()) {
        AiHeader(stringResource(R.string.jwsk_api_settings))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card {
                    OverlayDropdownPreference(title = stringResource(R.string.jwsk_provider),
                        items = AiProvider.entries.map { it.displayName }, selectedIndex = provider.ordinal,
                        onSelectedIndexChange = { provider = AiProvider.entries[it] })
                }
            }
            if (!snapshot.loaded) item {
                Text(stringResource(if (error == null) R.string.jwsk_loading else R.string.jwsk_ai_read_failed))
                if (error != null) TextButton(text = stringResource(R.string.jwsk_retry), onClick = model::reload)
            }
            item { ApiField(stringResource(R.string.jwsk_endpoint), endpoint) { endpoint = it } }
            if (provider == AiProvider.AZURE_OPENAI) {
                item { ApiField(stringResource(R.string.jwsk_deployment), deployment) { deployment = it } }
                item { ApiField("API version", apiVersion) { apiVersion = it } }
            } else item { ApiField(stringResource(R.string.jwsk_model), name) { name = it } }
            if (provider == AiProvider.AWS_BEDROCK) {
                item { ApiField(stringResource(R.string.jwsk_region), region) { region = it } }
                item {
                    Card {
                        OverlayDropdownPreference(title = stringResource(R.string.jwsk_authentication),
                            items = listOf("Bearer API key", "AWS SigV4"), selectedIndex = auth.ordinal,
                            onSelectedIndexChange = { auth = BedrockAuthentication.entries[it] })
                    }
                }
            }
            if (provider == AiProvider.AWS_BEDROCK && auth == BedrockAuthentication.AWS_CREDENTIALS) {
                item { ApiField("Access key ID", accessKey, true) { accessKey = it } }
                item { ApiField("Secret access key", secretKey, true) { secretKey = it } }
                item { ApiField("Session token", sessionToken, true) { sessionToken = it } }
            } else item { ApiField("API key / token", apiKey, true) { apiKey = it } }
            item { Text(stringResource(R.string.jwsk_key_retained), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            if (error == AiSettingsError.SAVE_FAILED) item { Text(stringResource(R.string.jwsk_ai_save_failed)) }
            item {
                TextButton(text = stringResource(if (saving) R.string.jwsk_saving else R.string.jwsk_save),
                    enabled = snapshot.loaded && !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = {
                        model.save(configuration, AiCredentialEdit(
                            apiKey.takeIf { it.isNotEmpty() }, accessKey.takeIf { it.isNotEmpty() },
                            secretKey.takeIf { it.isNotEmpty() }, sessionToken.takeIf { it.isNotEmpty() })) {
                            apiKey = ""; accessKey = ""; secretKey = ""; sessionToken = ""
                        }
                    })
            }
            item {
                TextButton(text = stringResource(R.string.jwsk_clear_keys), enabled = snapshot.loaded && !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = { model.save(configuration, AiCredentialEdit("", "", "", "")) })
            }
            item {
                Text(stringResource(if (summary?.isConfigured == true && configuration == saved)
                    R.string.jwsk_config_saved else R.string.jwsk_config_incomplete))
            }
        }
    }
}

@Composable
fun AiChatScreen() {
    val model = viewModel<AiViewModel>()
    val snapshot by model.settings.collectAsStateWithLifecycle()
    val chat by model.conversation.state.collectAsStateWithLifecycle()
    var prompt by remember { mutableStateOf("") }
    LaunchedEffect(chat.messages.size) {
        if (chat.messages.lastOrNull()?.role == MessageRole.ASSISTANT) prompt = ""
    }
    Column(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).imePadding().navigationBarsPadding()) {
        AiHeader(stringResource(R.string.jwsk_ai_assistant))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (snapshot.showUnconfiguredBadge) item { Text(stringResource(R.string.jwsk_config_in_settings)) }
            if (chat.messages.isEmpty()) item { Text(stringResource(R.string.jwsk_ai_intro)) }
            items(chat.messages) { message ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (message.role == MessageRole.USER) stringResource(R.string.jwsk_you) else "AI",
                        color = MiuixTheme.colorScheme.primary)
                    SelectionContainer { Text(message.text) }
                }
            }
            chat.error?.let { failure -> item {
                Text(stringResource(when (failure) {
                    AiError.CONFIGURATION -> R.string.jwsk_ai_configuration_error
                    AiError.AUTHENTICATION -> R.string.jwsk_ai_auth_error
                    AiError.RATE_LIMIT -> R.string.jwsk_ai_rate_limit
                    AiError.NETWORK -> R.string.jwsk_ai_network_error
                    AiError.TOO_LARGE -> R.string.jwsk_ai_too_large
                    else -> R.string.jwsk_ai_response_error
                }))
            } }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(value = prompt, onValueChange = { prompt = it.take(20_000) },
                enabled = !chat.sending,
                label = stringResource(R.string.jwsk_ai_message), modifier = Modifier.fillMaxWidth(), maxLines = 5)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = stringResource(if (chat.sending) R.string.jwsk_cancel else R.string.jwsk_send),
                    enabled = chat.sending || (snapshot.activeProfile?.isConfigured == true && prompt.isNotBlank()),
                    onClick = {
                        if (chat.sending) model.conversation.cancel() else model.conversation.send(prompt)
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                TextButton(text = stringResource(R.string.jwsk_clear_chat), onClick = { model.conversation.clear() },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
}
