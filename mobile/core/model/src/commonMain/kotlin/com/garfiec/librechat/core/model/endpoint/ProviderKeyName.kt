package com.garfiec.librechat.core.model.endpoint

import com.garfiec.librechat.core.model.EndpointConfig

/**
 * Resolves the storage key name used by `/api/keys` for a given endpoint.
 *
 * The endpoint name comes from the `endpointConfigs` map key, since the upstream
 * `/api/endpoints` response returns `Record<string, TConfig>` where the key is
 * authoritative for the name and `TConfig.name` is declared optional (and is
 * empirically null in real responses).
 *
 * When `endpoint.azure == true`, the key is stored under `"azureOpenAI"`
 * regardless of the endpoint's display name.
 */
fun resolveProviderKeyName(endpointName: String, endpoint: EndpointConfig?): String =
    if (endpoint?.azure == true) "azureOpenAI" else endpointName
