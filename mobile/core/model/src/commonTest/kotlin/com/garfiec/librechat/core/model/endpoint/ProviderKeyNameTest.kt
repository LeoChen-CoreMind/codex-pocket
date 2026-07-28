package com.garfiec.librechat.core.model.endpoint

import com.garfiec.librechat.core.model.EndpointConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * When `endpoint.azure == true`, the key is stored under `"azureOpenAI"` regardless
 * of the endpoint's display name.
 *
 * The endpoint name comes from the `endpointConfigs` map key, since the upstream
 * `/api/endpoints` response returns `Record<string, TConfig>` where the key is
 * authoritative for the name and `TConfig.name` is declared optional (and is
 * empirically null in real responses).
 */
class ProviderKeyNameTest {

    @Test
    fun azure_flag_remaps_to_azureOpenAI() {
        val config = EndpointConfig(name = "my-azure-deploy", azure = true)
        assertEquals("azureOpenAI", resolveProviderKeyName("my-azure-deploy", config))
    }

    @Test
    fun custom_endpoint_uses_its_name() {
        val config = EndpointConfig(name = "custom-claude", azure = null)
        assertEquals("custom-claude", resolveProviderKeyName("custom-claude", config))
    }

    @Test
    fun built_in_azureOpenAI_passes_through() {
        val config = EndpointConfig(name = "azureOpenAI", azure = null)
        assertEquals("azureOpenAI", resolveProviderKeyName("azureOpenAI", config))
    }

    @Test
    fun azure_false_uses_name() {
        val config = EndpointConfig(name = "openAI", azure = false)
        assertEquals("openAI", resolveProviderKeyName("openAI", config))
    }

    /** Regression: `endpoint.name = null` is the production-realistic shape (the upstream
     *  response leaves `TConfig.name` unset because the map key carries it). The helper must
     *  no longer throw — it falls through to the supplied [endpointName] argument. */
    @Test
    fun config_with_null_name_uses_passed_endpointName() {
        val config = EndpointConfig(name = null, azure = null)
        assertEquals("custom-claude", resolveProviderKeyName("custom-claude", config))
    }

    /** Regression: even with `endpoint.name` null, `azure == true` still remaps. */
    @Test
    fun azure_remap_works_when_config_name_is_null() {
        val config = EndpointConfig(name = null, azure = true)
        assertEquals("azureOpenAI", resolveProviderKeyName("my-azure", config))
    }

    /** Defensive: caller may not have a config (e.g., the endpoint is missing from
     *  `/api/config`). Helper falls through to the supplied name. */
    @Test
    fun null_config_falls_through_to_endpointName() {
        assertEquals("openAI", resolveProviderKeyName("openAI", null))
    }
}
