package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ToolCallType {
    @SerialName("function")
    FUNCTION,

    @SerialName("retrieval")
    RETRIEVAL,

    @SerialName("file_search")
    FILE_SEARCH,

    @SerialName("code_interpreter")
    CODE_INTERPRETER,

    @SerialName("tool_call")
    TOOL_CALL,
}
