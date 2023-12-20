package com.motorro.kopenaichat

import com.aallam.openai.api.model.ModelId

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