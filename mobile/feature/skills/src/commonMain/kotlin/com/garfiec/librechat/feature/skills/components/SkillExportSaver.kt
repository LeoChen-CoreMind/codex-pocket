package com.garfiec.librechat.feature.skills.components

import androidx.compose.runtime.Composable

@Composable
expect fun rememberSkillExportSaver(): (filename: String, bytes: ByteArray) -> Unit
