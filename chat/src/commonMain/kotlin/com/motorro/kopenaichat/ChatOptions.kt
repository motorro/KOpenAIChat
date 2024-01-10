package com.motorro.kopenaichat

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.assistant.AssistantId
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.api.thread.ThreadId

/**
 * Options for chat-completion
 * @param model Chat model ID
 * @param systemMessage Message to initialize the chat
 * @param maxMessages Maximum number of messages to keep
 * @param tools Tools configuration
 * @param logging Turns on HTTP logging
 */
data class ChatOptions(
    val model: ModelId,
    val systemMessage: String = "You are a helpful assistant",
    val maxMessages: Int = 30,
    val tools: ChatTools? = null,
    val logging: Boolean = false
)

/**
 * Options for assistant chat
 */
@OptIn(BetaOpenAI::class)
data class AssistantOptions constructor(
    val assistantId: AssistantId,
    val threadId: ThreadId,
    val maxRefetchMessages: Int? = 20,
    val toolsDispatcher: ToolsDispatcher? = null,
    val logging: Boolean = false
)