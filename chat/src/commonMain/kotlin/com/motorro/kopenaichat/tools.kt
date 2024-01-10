package com.motorro.kopenaichat

import com.aallam.openai.api.chat.Tool
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Runs client function when AI requests it
 */
interface ToolsDispatcher {
    /**
     * Dispatches the function call
     * @param name function name
     * @param args function arguments
     */
    suspend fun dispatch(name: String, args: JsonObject?): JsonElement
}

/**
 * Chat tools provision
 */
interface ChatTools {
    /**
     * Tools description
     */
    val tools: List<Tool>

    /**
     * Tools dispatcher
     */
    val dispatcher: ToolsDispatcher
}