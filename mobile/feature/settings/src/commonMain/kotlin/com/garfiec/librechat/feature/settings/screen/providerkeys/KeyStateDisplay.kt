package com.garfiec.librechat.feature.settings.screen.providerkeys

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_expired
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_loading
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_set_expires
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_set_never
import com.garfiec.librechat.feature.settings.resources.provider_keys_status_unset
import com.garfiec.librechat.feature.settings.state.providerkeys.formatRelativeExpiry
import org.jetbrains.compose.resources.stringResource

/**
 * Renders the localized status string for a [KeyState] using `bodySmall` typography.
 * Non-error states use `onSurfaceVariant`; [KeyState.Expired] uses `error`.
 */
@Composable
internal fun KeyStateDisplay(
    state: KeyState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        KeyState.Loading -> Text(
            stringResource(Res.string.provider_keys_status_loading),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is KeyState.Unset -> Text(
            stringResource(Res.string.provider_keys_status_unset),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        is KeyState.Set -> {
            if (state.neverExpires) {
                Text(
                    stringResource(Res.string.provider_keys_status_set_never),
                    modifier = modifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val relative = state.expiresAt?.let { formatRelativeExpiry(it) }.orEmpty()
                Text(
                    stringResource(Res.string.provider_keys_status_set_expires, relative),
                    modifier = modifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        KeyState.Expired -> Text(
            stringResource(Res.string.provider_keys_status_expired),
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
