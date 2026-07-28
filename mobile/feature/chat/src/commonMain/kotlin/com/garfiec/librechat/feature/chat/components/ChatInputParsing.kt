package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.feature.chat.model.PromptMentionDisplayData

/**
 * Extracts the @mention query from the input text.
 * Returns the text after the last '@' if there's no space after it (user is still typing),
 * or null if no active mention query.
 */
fun parseMentionQuery(text: String): String? {
    val atIndex = text.lastIndexOf('@')
    if (atIndex < 0) return null
    val afterAt = text.substring(atIndex + 1)
    return if (!afterAt.contains(' ')) afterAt else null
}

/**
 * Extracts the slash command query from the input text.
 * Returns the text after '/' if it starts the input and has no space (user is still typing),
 * or null if no active slash query.
 */
fun parseSlashQuery(text: String): String? {
    if (!text.startsWith("/")) return null
    val afterSlash = text.substring(1)
    return if (!afterSlash.contains(' ')) afterSlash else null
}

/**
 * Filters prompt suggestions matching the given @mention query.
 * Matches against name and command, returns at most 5 results.
 */
fun filterMatchingPrompts(
    query: String,
    prompts: List<PromptMentionDisplayData>,
): List<PromptMentionDisplayData> {
    if (prompts.isEmpty()) return emptyList()
    return prompts.filter { group ->
        group.name.contains(query, ignoreCase = true) ||
            group.command?.contains(query, ignoreCase = true) == true
    }.take(5)
}

/**
 * Filters prompt suggestions matching the given slash command query.
 * Only matches against the command field, returns at most 5 results.
 */
fun filterMatchingSlashCommands(
    query: String,
    commands: List<PromptMentionDisplayData>,
): List<PromptMentionDisplayData> {
    if (commands.isEmpty()) return emptyList()
    return commands.filter { group ->
        val cmd = group.command
        cmd != null && cmd.contains(query, ignoreCase = true)
    }.take(5)
}
