package com.garfiec.librechat.feature.settings.viewmodel.providerkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.KeyRepository
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.endpoint.resolveProviderKeyName
import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_api_key_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_api_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_api_version
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_deployment
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_azure_instance
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_base_url_label
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_access_key_id
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_bedrock_secret_access_key
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_invalid
import com.garfiec.librechat.feature.settings.resources.provider_keys_field_google_service_key_or_gemini
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_failed
import com.garfiec.librechat.feature.settings.resources.provider_keys_revoke_success
import com.garfiec.librechat.feature.settings.resources.provider_keys_save_failed
import com.garfiec.librechat.feature.settings.resources.provider_keys_save_success
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyExpiry
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormKind
import com.garfiec.librechat.feature.settings.state.providerkeys.ProviderKeyFormState
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyEffect
import com.garfiec.librechat.feature.settings.state.providerkeys.SetProviderKeyUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Clock

/**
 * Per-dialog ViewModel for the Set Provider Key bottom sheet.
 *
 * Wire shape matches web's SetKeyDialog.
 */
@Suppress("TooManyFunctions")
class SetProviderKeyViewModel(
    private val endpointName: String,
    private val keyRepository: KeyRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetProviderKeyUiState(endpointName = endpointName))
    val uiState: StateFlow<SetProviderKeyUiState> = _uiState.asStateFlow()

    // One-shot effects via Channel — exactly-once delivery, mirrors ChatViewModel.userKeyErrors.
    private val _effects = Channel<SetProviderKeyEffect>(Channel.BUFFERED)
    val effects: Flow<SetProviderKeyEffect> = _effects.receiveAsFlow()

    init {
        // Run the synchronous form-variant resolution inline so the default `form` value
        // (a benign placeholder) is never observed by the UI. The host dialog's
        // `LaunchedEffect(endpointName)` is the single source of `refreshKeyState()` calls
        // (including the first open), so we don't fire a duplicate fetch here.
        initFromConfig()
    }

    /**
     * Re-fetches the current per-endpoint key state and updates [SetProviderKeyUiState].
     *
     * Called from the host dialog's `LaunchedEffect(endpointName)` on each open so the VM
     * refreshes its banner. Surfaces `KeyState.Loading` while the GET is in flight so the
     * banner never renders a stale value carried over from a prior fetch.
     */
    fun refreshKeyState() {
        viewModelScope.launch { refreshKeyStateInternal() }
    }

    private fun initFromConfig() {
        val config = resolveEndpoint()
        val kind = resolveFormKind(endpointName, config)
        val displayLabel = config.modelDisplayLabel ?: endpointName
        _uiState.update {
            it.copy(
                displayLabel = displayLabel,
                formKind = kind,
                form = initialFormState(kind, config),
            )
        }
    }

    /** Re-reads the endpoint config on every call — config can change after `fetchEndpoints()`. */
    private fun resolveEndpoint(): EndpointConfig =
        configRepository.endpointConfigs.value[endpointName]
            ?: EndpointConfig(name = endpointName)

    private fun initialFormState(
        kind: ProviderKeyFormKind,
        config: EndpointConfig,
    ): ProviderKeyFormState = when (kind) {
        ProviderKeyFormKind.OPENAI,
        ProviderKeyFormKind.CUSTOM -> ProviderKeyFormState.ApiKeyAndOptionalBaseUrl(
            userProvideURL = config.userProvideURL == true,
            isOpenAIBase = kind == ProviderKeyFormKind.OPENAI,
        )
        ProviderKeyFormKind.AZURE -> ProviderKeyFormState.Azure()
        ProviderKeyFormKind.GOOGLE -> ProviderKeyFormState.Google()
        ProviderKeyFormKind.BEDROCK -> ProviderKeyFormState.Bedrock()
        ProviderKeyFormKind.OTHER -> ProviderKeyFormState.Other()
    }

    private suspend fun refreshKeyStateInternal() {
        // Surface Loading while the GET is in flight so the banner never briefly renders
        // a stale "never expire" / expiry value carried over from a prior fetch.
        _uiState.update { it.copy(currentKeyState = KeyState.Loading) }
        val keyName = resolveProviderKeyName(endpointName, resolveEndpoint())
        // Fail-closed: any error on the underlying GET resolves to KeyState.Unset so the
        // dialog renders predictable "Not set" rather than a stale prior value.
        val state = keyRepository.fetchKeyState(keyName).getOrNull() ?: KeyState.Unset
        _uiState.update { it.copy(currentKeyState = state) }
    }

    fun selectExpiry(expiry: ProviderKeyExpiry) {
        _uiState.update { it.copy(expiry = expiry) }
    }

    // OpenAI / Custom / Other updaters
    fun updateApiKey(value: String) {
        _uiState.update { state ->
            state.copy(
                form = when (val f = state.form) {
                    is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl -> f.copy(apiKey = value)
                    is ProviderKeyFormState.Other -> f.copy(apiKey = value)
                    else -> f
                },
            )
        }
    }

    fun updateBaseUrl(value: String) {
        _uiState.update { state ->
            state.copy(
                form = when (val f = state.form) {
                    is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl -> f.copy(baseURL = value)
                    else -> f
                },
            )
        }
    }

    // Azure updaters
    fun updateAzureField(field: AzureField, value: String) {
        _uiState.update { state ->
            val f = state.form as? ProviderKeyFormState.Azure ?: return@update state
            val updated = when (field) {
                AzureField.API_KEY -> f.copy(azureOpenAIApiKey = value)
                AzureField.INSTANCE -> f.copy(azureOpenAIApiInstanceName = value)
                AzureField.DEPLOYMENT -> f.copy(azureOpenAIApiDeploymentName = value)
                AzureField.VERSION -> f.copy(azureOpenAIApiVersion = value)
            }
            state.copy(form = updated)
        }
    }

    // Google updaters
    fun updateGoogleServiceKey(value: String) {
        val validation = validateGoogleServiceKey(value)
        _uiState.update { state ->
            val f = state.form as? ProviderKeyFormState.Google ?: return@update state
            state.copy(
                form = f.copy(
                    serviceKeyJson = value,
                    serviceKeyImportSuccess = validation == ServiceKeyValidation.Valid,
                    hasServiceKeyImportError = validation == ServiceKeyValidation.Invalid,
                ),
            )
        }
    }

    fun updateGoogleApiKey(value: String) {
        _uiState.update { state ->
            val f = state.form as? ProviderKeyFormState.Google ?: return@update state
            state.copy(form = f.copy(geminiApiKey = value))
        }
    }

    /** Called when the file-picker delivers raw JSON contents (Android only). */
    fun onGoogleServiceKeyFileRead(jsonContents: String?) {
        if (jsonContents.isNullOrBlank()) return
        val validation = validateGoogleServiceKey(jsonContents)
        _uiState.update { state ->
            // CAS-retry race: another mutation may have flipped the form away from Google
            // between the file-read callback and this update. Skip the copy in that case so
            // we don't clobber a non-Google variant with Google fields.
            val f = state.form as? ProviderKeyFormState.Google ?: return@update state
            state.copy(
                form = f.copy(
                    serviceKeyJson = jsonContents,
                    serviceKeyImportSuccess = validation == ServiceKeyValidation.Valid,
                    hasServiceKeyImportError = validation == ServiceKeyValidation.Invalid,
                ),
            )
        }
        if (validation != ServiceKeyValidation.Valid) {
            viewModelScope.launch {
                _effects.send(
                    SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_field_google_invalid),
                )
            }
        }
    }

    // Bedrock updaters
    fun updateBedrockField(field: BedrockField, value: String) {
        _uiState.update { state ->
            val f = state.form as? ProviderKeyFormState.Bedrock ?: return@update state
            val updated = when (field) {
                BedrockField.ACCESS_KEY_ID -> f.copy(accessKeyId = value)
                BedrockField.SECRET_ACCESS_KEY -> f.copy(secretAccessKey = value)
                BedrockField.SESSION_TOKEN -> f.copy(sessionToken = value)
            }
            state.copy(form = updated)
        }
    }

    /**
     * Validates the form, builds the wire `value`, and dispatches the update. On validation
     * failure emits [SetProviderKeyEffect.RequiredFieldsMissing] once and short-circuits.
     */
    fun save() {
        val state = _uiState.value
        // Run the synchronous validator outside the launched scope: a Missing result emits
        // RequiredFieldsMissing once at the top and short-circuits before we touch isSaving
        // or fire a network call.
        val payload = when (val result = buildPayload(state.form)) {
            is BuildResult.Ok -> result.payload
            is BuildResult.Missing -> {
                viewModelScope.launch {
                    _effects.send(SetProviderKeyEffect.RequiredFieldsMissing(result.fields))
                }
                return
            }
        }
        viewModelScope.launch {
            val expiresAt = computeExpiresAt(state.expiry)
            _uiState.update { it.copy(isSaving = true) }
            val config = resolveEndpoint()
            val keyName = resolveProviderKeyName(endpointName, config)
            val request = UpdateKeyRequest(name = keyName, value = payload, expiresAt = expiresAt)
            when (keyRepository.updateKey(request)) {
                is Result.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            isSaving = false,
                            form = initialFormState(current.formKind, config),
                        )
                    }
                    refreshKeyStateInternal()
                    // Single Mutated emit carries both the success-toast and the lifecycle
                    // signal. The dialog's effect collector emits the message into the
                    // parent snackbar before invoking onMutationSuccess + onDismiss.
                    _effects.send(
                        SetProviderKeyEffect.Mutated(Res.string.provider_keys_save_success),
                    )
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _effects.send(
                        SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_save_failed),
                    )
                }
                // safeApiCall does not emit Loading — the branch is unreachable.
                is Result.Loading -> Unit
            }
        }
    }

    fun revoke() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRevoking = true) }
            val config = resolveEndpoint()
            val keyName = resolveProviderKeyName(endpointName, config)
            when (keyRepository.deleteKey(keyName)) {
                is Result.Success -> {
                    // Reset the form to a fresh blank instance of the same kind so a
                    // subsequent reopen of this dialog doesn't show preloaded masked dots
                    // from the prior typed-but-now-revoked secret.
                    _uiState.update { current ->
                        current.copy(
                            isRevoking = false,
                            currentKeyState = KeyState.Unset,
                            form = initialFormState(current.formKind, config),
                            expiry = DEFAULT_EXPIRY,
                        )
                    }
                    _effects.send(
                        SetProviderKeyEffect.Mutated(Res.string.provider_keys_revoke_success),
                    )
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isRevoking = false) }
                    _effects.send(
                        SetProviderKeyEffect.ShowMessage(Res.string.provider_keys_revoke_failed),
                    )
                }
                // safeApiCall does not emit Loading — the branch is unreachable.
                is Result.Loading -> Unit
            }
        }
    }

    /**
     * Pure builder result. [Ok] carries the wire payload; [Missing] carries the localized
     * field-label resources that need to be filled in. The host [save] resolves [Missing] to
     * a single [SetProviderKeyEffect.RequiredFieldsMissing] emit at the suspend boundary.
     */
    internal sealed class BuildResult {
        data class Ok(val payload: String) : BuildResult()
        data class Missing(val fields: List<StringResource>) : BuildResult()
    }

    /** Synchronous: builds the wire `value` per endpoint variant or reports missing fields. */
    internal fun buildPayload(form: ProviderKeyFormState): BuildResult = when (form) {
        is ProviderKeyFormState.ApiKeyAndOptionalBaseUrl ->
            apiKeyAndOptionalBaseUrl(
                apiKey = form.apiKey,
                userProvideURL = form.userProvideURL,
                baseURL = form.baseURL,
                isOpenAIBase = form.isOpenAIBase,
            )
        is ProviderKeyFormState.Azure -> buildAzurePayload(form)
        is ProviderKeyFormState.Google -> buildGooglePayload(form)
        is ProviderKeyFormState.Bedrock -> buildBedrockPayload(form)
        is ProviderKeyFormState.Other -> {
            if (form.apiKey.isBlank()) {
                BuildResult.Missing(listOf(Res.string.provider_keys_field_api_key_label))
            } else {
                BuildResult.Ok(form.apiKey)
            }
        }
    }

    private fun buildAzurePayload(form: ProviderKeyFormState.Azure): BuildResult {
        val missing = buildList {
            if (form.azureOpenAIApiKey.isEmpty()) add(Res.string.provider_keys_field_azure_api_key)
            if (form.azureOpenAIApiInstanceName.isEmpty()) add(Res.string.provider_keys_field_azure_instance)
            if (form.azureOpenAIApiDeploymentName.isEmpty()) add(Res.string.provider_keys_field_azure_deployment)
            if (form.azureOpenAIApiVersion.isEmpty()) add(Res.string.provider_keys_field_azure_api_version)
        }
        if (missing.isNotEmpty()) return BuildResult.Missing(missing)
        // Azure wire shape: outer JSON wraps a stringified inner quad in `apiKey`, with
        // an empty `baseURL`. Backend `JSON.parse(JSON.parse(value).apiKey)` requires this.
        val inner = encodeKeyPayload {
            put("azureOpenAIApiKey", form.azureOpenAIApiKey)
            put("azureOpenAIApiInstanceName", form.azureOpenAIApiInstanceName)
            put("azureOpenAIApiDeploymentName", form.azureOpenAIApiDeploymentName)
            put("azureOpenAIApiVersion", form.azureOpenAIApiVersion)
        }
        return BuildResult.Ok(
            encodeKeyPayload {
                put("apiKey", inner)
                put("baseURL", "")
            },
        )
    }

    private fun buildGooglePayload(form: ProviderKeyFormState.Google): BuildResult {
        val hasService = form.serviceKeyJson.isNotEmpty()
        val hasGemini = form.geminiApiKey.isNotEmpty()
        if (!hasService && !hasGemini) {
            return BuildResult.Missing(
                listOf(Res.string.provider_keys_field_google_service_key_or_gemini),
            )
        }
        // Always emit both keys with `""` for empty fields; backend ignores empty strings on
        // either path, so per-field touched tracking is unnecessary.
        return BuildResult.Ok(
            encodeKeyPayload {
                put("GOOGLE_SERVICE_KEY", JsonPrimitive(form.serviceKeyJson))
                put("GOOGLE_API_KEY", JsonPrimitive(form.geminiApiKey))
            },
        )
    }

    private fun buildBedrockPayload(form: ProviderKeyFormState.Bedrock): BuildResult {
        val missing = buildList {
            if (form.accessKeyId.isEmpty()) add(Res.string.provider_keys_field_bedrock_access_key_id)
            if (form.secretAccessKey.isEmpty()) add(Res.string.provider_keys_field_bedrock_secret_access_key)
        }
        if (missing.isNotEmpty()) return BuildResult.Missing(missing)
        // Backend `JSON.parse(key)` shape — `sessionToken` field is omitted entirely when blank.
        return BuildResult.Ok(
            encodeKeyPayload {
                put("accessKeyId", form.accessKeyId)
                put("secretAccessKey", form.secretAccessKey)
                if (form.sessionToken.isNotEmpty()) put("sessionToken", form.sessionToken)
            },
        )
    }

    /**
     * Shared payload builder for OpenAI and Custom — they have identical wire shape but
     * different baseURL validation rules.
     *
     * Web's `SetKeyDialog.tsx:226-228` skips baseURL validation for any endpoint where
     * `isOpenAIBase` is true (openAI / assistants / azureOpenAI / azureAssistants —
     * matched here as `formKind == OPENAI`, since `azureAssistants` also routes to OPENAI
     * per `resolveFormKind`). For Custom, baseURL is required when `userProvideURL=true`.
     * The wire envelope always carries `{ apiKey, baseURL }`, with `baseURL = ""` if the
     * user left it blank.
     */
    private fun apiKeyAndOptionalBaseUrl(
        apiKey: String,
        userProvideURL: Boolean,
        baseURL: String,
        isOpenAIBase: Boolean,
    ): BuildResult {
        val missing = buildList {
            if (apiKey.isEmpty()) add(Res.string.provider_keys_field_api_key_label)
            if (!isOpenAIBase && userProvideURL && baseURL.isEmpty()) {
                add(Res.string.provider_keys_field_base_url_label)
            }
        }
        if (missing.isNotEmpty()) return BuildResult.Missing(missing)
        return BuildResult.Ok(
            encodeKeyPayload {
                put("apiKey", apiKey)
                put("baseURL", baseURL)
            },
        )
    }

    /** Encodes a JsonObject literal to a wire string. */
    private fun encodeKeyPayload(builder: JsonObjectBuilder.() -> Unit): String =
        Json.encodeToString(JsonObject.serializer(), buildJsonObject(builder))

    private fun computeExpiresAt(expiry: ProviderKeyExpiry): String {
        val duration = expiry.duration ?: return ""
        return (Clock.System.now() + duration).toString()
    }

    /**
     * Three-valued result for live validation of a pasted/typed service-key JSON.
     *
     * - [Empty] — input is blank; render no banner (neutral).
     * - [Valid] — JSON parses, shape looks correct.
     * - [Invalid] — JSON fails to parse or required fields are missing/wrong type.
     */
    internal enum class ServiceKeyValidation { Empty, Valid, Invalid }

    /**
     * Validates a Google service-key JSON paste-or-import. See [ServiceKeyValidation].
     *
     * Mobile-side validation only catches obvious user errors (empty / missing fields,
     * invalid email shape). The server validates the private-key shape on save; we do
     * not duplicate that here.
     */
    internal fun validateGoogleServiceKey(json: String): ServiceKeyValidation {
        if (json.isBlank()) return ServiceKeyValidation.Empty
        val parsed = runCatching { Json.parseToJsonElement(json) }.getOrNull()
            ?: return ServiceKeyValidation.Invalid
        val obj = (parsed as? JsonObject) ?: return ServiceKeyValidation.Invalid
        // Safe-cast to JsonPrimitive — the `.jsonPrimitive` extension throws on objects/arrays,
        // which would crash mid-keystroke on adversarial input.
        val email = (obj["client_email"] as? JsonPrimitive)?.contentOrNullSafe()
        val projectId = (obj["project_id"] as? JsonPrimitive)?.contentOrNullSafe()
        val privateKey = (obj["private_key"] as? JsonPrimitive)?.contentOrNullSafe()
        val ok = !email.isNullOrBlank() &&
            !projectId.isNullOrBlank() &&
            !privateKey.isNullOrBlank() &&
            email.contains('@')
        return if (ok) ServiceKeyValidation.Valid else ServiceKeyValidation.Invalid
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else null

    enum class AzureField { API_KEY, INSTANCE, DEPLOYMENT, VERSION }
    enum class BedrockField { ACCESS_KEY_ID, SECRET_ACCESS_KEY, SESSION_TOKEN }

    companion object {
        /**
         * Default expiry window selected on dialog open and on revoke success. Matches the
         * web client's SetKeyDialog default.
         */
        internal val DEFAULT_EXPIRY: ProviderKeyExpiry = ProviderKeyExpiry.TWELVE_HOURS

        /**
         * Maps an endpoint to its form variant.
         *
         * Takes [endpointName] explicitly (mirrors `resolveProviderKeyName`) because
         * `EndpointConfig.name` is empirically null in real `/api/endpoints` responses
         * (the map key is authoritative). Without this, built-in endpoints like
         * `openAI` whose config has no `type` overlay fall through to OTHER.
         *
         * **`azureAssistants` parity note.** Upstream's `SetKeyDialog.tsx:215` defines
         * `isAzure = endpoint === EModelEndpoint.azureOpenAI` (strict equality, no
         * `azureAssistants`), and `OpenAIConfig.tsx:13` makes the same check at render
         * time. The result is that web treats `azureAssistants` as plain OpenAI — one
         * `apiKey` field, no Azure quad-form, no nested JSON envelope. This looks like
         * an upstream oversight, but we mirror it exactly to keep mobile and web wire
         * shapes identical (Option A). Anyone who actually wants Azure semantics on a
         * custom endpoint sets `config.azure = true`, which still routes here via the
         * short-circuit above regardless of `type`.
         */
        fun resolveFormKind(endpointName: String, endpoint: EndpointConfig): ProviderKeyFormKind {
            if (endpoint.azure == true) return ProviderKeyFormKind.AZURE
            val effective = endpoint.type ?: endpointName
            return when (effective) {
                "azureOpenAI" -> ProviderKeyFormKind.AZURE
                "openAI", "assistants", "azureAssistants" -> ProviderKeyFormKind.OPENAI
                "google" -> ProviderKeyFormKind.GOOGLE
                "custom" -> ProviderKeyFormKind.CUSTOM
                "bedrock" -> ProviderKeyFormKind.BEDROCK
                else -> ProviderKeyFormKind.OTHER
            }
        }
    }
}
