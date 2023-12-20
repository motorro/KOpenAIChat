package com.motorro.kopenaichat

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Chat conversation
 * @param options Chat options
 * @param userChannel A channel to send user messages
 */
fun chatConversation(
    token: String,
    options: ChatOptions,
    userChannel: ReceiveChannel<String>,
    clock: Clock = Clock.System
): Flow<ChatState> = flow {
    val openai = OpenAI(
        OpenAIConfig(
            token = token,
            logging = LoggingConfig(
                logLevel = when (options.logging) {
                    true -> LogLevel.Info
                    else -> LogLevel.None
                }
            )
        )
    )

    val messages: MutableList<ChatMessage> = mutableListOf(
        ChatMessage(ChatRole.System, options.systemMessage)
    )
    val verbalMessages: MutableList<VerbalMessage> = mutableListOf()

    fun pushMessage(message: ChatMessage) {
        messages.add(message)
        if (messages.size > options.maxMessages) {
            messages.removeAt(0)
        }
        val content = message.content ?: return
        if (ChatRole.User == message.role || ChatRole.Assistant == message.role) {
            verbalMessages.add(
                VerbalMessage(
                    role = if (ChatRole.User == message.role) Role.USER else Role.ASSISTANT,
                    message = content,
                    clock.now()
                )
            )
        }
    }

    suspend fun emitAi() {
        emit(ChatState.AwaitingAi(verbalMessages))
    }

    suspend fun emitUser() {
        emit(ChatState.AwaitingUser(verbalMessages))
    }

    suspend fun proceed() {
        emitAi()
        val tools = options.tools
        val completion = openai.chatCompletion(
            ChatCompletionRequest(
                model = options.model,
                messages = messages,
                tools = tools?.tools
            )
        )
        val message = completion.choices[0].message
        pushMessage(message)

        val dispatcher = tools?.dispatcher ?: return

        var processed = 0
        for (toolCall in message.toolCalls.orEmpty()) {
            if (toolCall !is ToolCall.Function) {
                continue
            }
            val functionResponse = dispatcher.dispatch(toolCall.function.name, toolCall.function.argumentsAsJsonOrNull())
            messages.add(
                ChatMessage(
                    role = ChatRole.Tool,
                    toolCallId = toolCall.id,
                    name = toolCall.function.name,
                    content = json.encodeToString(functionResponse)
                )
            )
            ++processed
        }

        if (processed > 0) {
            proceed()
        }
    }

    proceed()
    while (currentCoroutineContext().isActive) {
        emitUser()
        val message = userChannel.receiveCatching().getOrNull() ?: break
        pushMessage(ChatMessage(ChatRole.User, message))
        proceed()
    }
}

private val json : Json = Json {
    isLenient = true
}
