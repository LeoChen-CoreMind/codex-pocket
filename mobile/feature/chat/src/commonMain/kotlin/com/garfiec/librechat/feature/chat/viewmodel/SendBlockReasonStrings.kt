package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Composable
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.send_block_agent_not_available
import com.garfiec.librechat.feature.chat.resources.send_block_agents_unavailable
import com.garfiec.librechat.feature.chat.resources.send_block_model_load_failed
import com.garfiec.librechat.feature.chat.resources.send_block_model_not_available
import com.garfiec.librechat.feature.chat.resources.send_block_select_agent
import com.garfiec.librechat.feature.chat.resources.send_block_select_model
import org.jetbrains.compose.resources.stringResource

@Composable
fun SendBlockReason.asString(): String = when (this) {
    SendBlockReason.SelectAgent -> stringResource(Res.string.send_block_select_agent)
    SendBlockReason.SelectModel -> stringResource(Res.string.send_block_select_model)
    SendBlockReason.AgentsUnavailable -> stringResource(Res.string.send_block_agents_unavailable)
    SendBlockReason.AgentNotAvailable -> stringResource(Res.string.send_block_agent_not_available)
    SendBlockReason.ModelNotAvailable -> stringResource(Res.string.send_block_model_not_available)
    SendBlockReason.ModelLoadFailed -> stringResource(Res.string.send_block_model_load_failed)
}
