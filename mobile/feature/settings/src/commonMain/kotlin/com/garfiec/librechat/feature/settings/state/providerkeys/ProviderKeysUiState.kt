package com.garfiec.librechat.feature.settings.state.providerkeys

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_12h
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_1d
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_2h
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_30d
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_30m
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_7d
import com.garfiec.librechat.feature.settings.resources.provider_keys_expiry_never
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * One row in the Provider API Keys list.
 *
 * @property endpointName the display name of the endpoint (also used as the entry key in
 *   `endpointConfigs`); NOT necessarily the storage key — see
 *   [com.garfiec.librechat.core.model.endpoint.resolveProviderKeyName].
 * @property config the resolved endpoint config from `/api/config`.
 * @property keyState the current per-endpoint key state (Unset, Set with expiry, or Expired).
 */
@Immutable
data class ProviderKeyEntry(
    val endpointName: String,
    val config: EndpointConfig,
    val keyState: KeyState = KeyState.Loading,
)

/**
 * Top-level UI state for the Provider API Keys list screen.
 */
@Immutable
data class ProviderKeysUiState(
    val isLoading: Boolean = true,
    val entries: List<ProviderKeyEntry> = emptyList(),
    val error: String? = null,
    val pendingDialogEndpoint: String? = null,
    val showRevokeAllConfirm: Boolean = false,
    val isRevokingAll: Boolean = false,
    val transientMessage: String? = null,
)

/**
 * Expiry presets matching web's `EXPIRY` enum (SetKeyDialog.tsx:49-57).
 * `null` [duration] is the wire-equivalent of "never" — encoded as an empty string.
 */
enum class ProviderKeyExpiry(val label: StringResource, val duration: Duration?) {
    THIRTY_MINUTES(Res.string.provider_keys_expiry_30m, 30.minutes),
    TWO_HOURS(Res.string.provider_keys_expiry_2h, 2.hours),
    TWELVE_HOURS(Res.string.provider_keys_expiry_12h, 12.hours),
    ONE_DAY(Res.string.provider_keys_expiry_1d, 1.days),
    ONE_WEEK(Res.string.provider_keys_expiry_7d, 7.days),
    ONE_MONTH(Res.string.provider_keys_expiry_30d, 30.days),
    NEVER(Res.string.provider_keys_expiry_never, null),
}

/**
 * Endpoint-form variants — drives both the form layout and the wire payload shape.
 */
enum class ProviderKeyFormKind { OPENAI, AZURE, GOOGLE, CUSTOM, BEDROCK, OTHER }

/**
 * Per-endpoint form input states. Each variant is a separate type so editors can be
 * type-safe and field validation lives close to the form definition.
 */
@Immutable
sealed class ProviderKeyFormState {
    /**
     * apiKey + optional baseURL pair. Used by both OpenAI/Assistants and Custom endpoints —
     * the variant differentiation (dialog labels, baseURL required vs optional copy) is
     * driven by [SetProviderKeyUiState.formKind] / [ProviderKeyFormState.userProvideURL].
     *
     * [isOpenAIBase] mirrors upstream's `isOpenAIBase` predicate in `SetKeyDialog.tsx:217`
     * (`openAI`, `assistants`, `azureOpenAI`, `azureAssistants`). When true, baseURL is
     * always optional even if `userProvideURL=true`; when false (Custom), baseURL is
     * required when `userProvideURL=true`. Set once at `initialFormState` time and never
     * mutates with form input, so it lives on the form state alongside `userProvideURL`
     * rather than being re-read from `formKind` inside the payload builder.
     */
    @Immutable
    data class ApiKeyAndOptionalBaseUrl(
        val apiKey: String = "",
        val baseURL: String = "",
        val userProvideURL: Boolean = false,
        val isOpenAIBase: Boolean = false,
    ) : ProviderKeyFormState()

    /** Azure quad: 4 required fields. */
    @Immutable
    data class Azure(
        val azureOpenAIApiKey: String = "",
        val azureOpenAIApiInstanceName: String = "",
        val azureOpenAIApiDeploymentName: String = "",
        val azureOpenAIApiVersion: String = "",
    ) : ProviderKeyFormState()

    /** Google: paste-or-import service-key JSON + optional Gemini key. */
    @Immutable
    data class Google(
        val serviceKeyJson: String = "",
        val geminiApiKey: String = "",
        val hasServiceKeyImportError: Boolean = false,
        val serviceKeyImportSuccess: Boolean = false,
    ) : ProviderKeyFormState()

    /**
     * AWS Bedrock: structured creds (accessKeyId + secretAccessKey + optional sessionToken).
     *
     * **Deliberate divergence from web.** Upstream's `SetKeyDialog.tsx:31-39`
     * `endpointComponents` map has no entry for `bedrock`, so it falls through to the
     * default `OtherConfig` (one plaintext text field, raw string wire). AWS creds
     * are fundamentally three independent values, so feeding them through a single
     * text input requires the user to type or paste an ad-hoc concatenation that
     * the server's Bedrock handler then has to guess at. Mobile sends a JSON envelope
     * `{ accessKeyId, secretAccessKey, sessionToken? }` instead — the same shape the
     * `@librechat/agents` backend expects on the wire. If upstream ever adds a real
     * `BedrockConfig.tsx`, we can converge then.
     */
    @Immutable
    data class Bedrock(
        val accessKeyId: String = "",
        val secretAccessKey: String = "",
        val sessionToken: String = "",
    ) : ProviderKeyFormState()

    /** Fall-through for anthropic + truly unknown endpoints: single plaintext key. */
    @Immutable
    data class Other(
        val apiKey: String = "",
    ) : ProviderKeyFormState()
}

/**
 * UI state for the Set Provider Key dialog (one instance per endpoint).
 */
@Immutable
data class SetProviderKeyUiState(
    val endpointName: String = "",
    val displayLabel: String = "",
    val formKind: ProviderKeyFormKind = ProviderKeyFormKind.OTHER,
    val form: ProviderKeyFormState = ProviderKeyFormState.Other(),
    val expiry: ProviderKeyExpiry = ProviderKeyExpiry.TWELVE_HOURS,
    val currentKeyState: KeyState = KeyState.Loading,
    val isSaving: Boolean = false,
    val isRevoking: Boolean = false,
)

/**
 * One-shot effects emitted by the dialog VM.
 */
@Immutable
sealed class SetProviderKeyEffect {
    /**
     * Non-mutating message — display a localized snackbar without dismissing the dialog or
     * triggering a parent refresh. Used for failures (save error, revoke error) and form
     * validation hints (invalid Google service-account JSON).
     */
    @Immutable
    data class ShowMessage(val message: StringResource) : SetProviderKeyEffect()

    /**
     * Save or revoke succeeded — parent should refresh + the dialog should dismiss. Carries
     * an optional snackbar message so the success-toast and lifecycle signal travel together
     * instead of as two separate `ShowMessage` + `Mutated` emissions.
     */
    @Immutable
    data class Mutated(val message: StringResource? = null) : SetProviderKeyEffect()

    /**
     * Validation rejection — payload is the list of missing field-label string resources.
     *
     * Resolved to localized text in the dialog via `getString(...)` and joined for display.
     * Wire field IDs (e.g. `apiKey`, `azureOpenAIApiKey`) stay internal to the VM/payload
     * builders; only the user-facing label set is plumbed here.
     */
    @Immutable
    data class RequiredFieldsMissing(val fields: List<StringResource>) : SetProviderKeyEffect()
}
