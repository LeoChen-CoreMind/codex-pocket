package com.garfiec.librechat.feature.chat.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.model.error.UserKeyError
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_cta
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_expired
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_invalid
import com.garfiec.librechat.feature.chat.resources.provider_keys_chat_error_no_key
import com.garfiec.librechat.feature.chat.viewmodel.ChatViewModel
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Collects [ChatViewModel.userKeyErrors] and surfaces a snackbar with a deep-link CTA
 * to Settings → Provider API Keys. The expired-key template is resolved via the
 * suspend `getString` overload because String.format isn't available in Kotlin/Native.
 *
 * The endpoint name from the typed error is forwarded so the destination can auto-open
 * the Set Key dialog for that endpoint; null endpoint falls through to the list view.
 */
@Composable
fun UserKeyErrorSnackbarEffect(
    viewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState,
    onNavigateToProviderKeys: (endpointName: String?) -> Unit,
) {
    val providerKeyErrorNoKey = stringResource(Res.string.provider_keys_chat_error_no_key)
    val providerKeyErrorInvalid = stringResource(Res.string.provider_keys_chat_error_invalid)
    val providerKeyErrorCta = stringResource(Res.string.provider_keys_chat_error_cta)
    val currentOnNavigateToProviderKeys by rememberUpdatedState(onNavigateToProviderKeys)
    LaunchedEffect(viewModel) {
        viewModel.userKeyErrors.collect { error ->
            val message = when (error) {
                is UserKeyError.NoUserKey -> providerKeyErrorNoKey
                is UserKeyError.ExpiredUserKey -> getString(
                    Res.string.provider_keys_chat_error_expired,
                    error.endpoint.orEmpty(),
                    error.expiredAt,
                )
                is UserKeyError.InvalidUserKey -> providerKeyErrorInvalid
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = providerKeyErrorCta,
            )
            if (result == SnackbarResult.ActionPerformed) {
                currentOnNavigateToProviderKeys(error.endpoint)
            }
        }
    }
}
