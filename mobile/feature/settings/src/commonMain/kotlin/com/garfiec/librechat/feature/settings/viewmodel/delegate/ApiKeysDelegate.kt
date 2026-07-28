package com.garfiec.librechat.feature.settings.viewmodel.delegate

/**
 * Placeholder delegate for API key CRUD operations.
 *
 * The actual API key management (list, create, delete) already lives in its own
 * [ApiKeysViewModel] with a dedicated screen. This delegate exists only to handle
 * the "Revoke All Keys" action that lives on the main settings screen.
 *
 * Since revokeAllKeys is closely tied to the data management section (cache clearing,
 * shared links, etc.), it is implemented in [DataManagementDelegate] instead.
 * This file is kept as a marker for future expansion if API key operations
 * need to be surfaced directly on the settings screen.
 */
