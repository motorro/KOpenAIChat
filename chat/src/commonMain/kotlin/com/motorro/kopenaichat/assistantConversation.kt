package com.motorro.kopenaichat

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.core.SortOrder
import com.aallam.openai.api.core.Status
import com.aallam.openai.api.message.Message
import com.aallam.openai.api.message.MessageContent
import com.aallam.openai.api.message.MessageId
import com.aallam.openai.api.message.MessageRequest
import com.aallam.openai.api.run.RequiredAction
import com.aallam.openai.api.run.Run
import com.aallam.openai.api.run.RunRequest
import com.aallam.openai.api.run.ToolOutput
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.aallam.openai.api.core.Role as ChatRole

@OptIn(BetaOpenAI::class)
suspend fun assistantConversation(
    openAi: OpenAI,
    options: AssistantOptions,
    userChannel: ReceiveChannel<String>,
    clock: Clock = Clock.System
) = flow<ChatState> {

    val verbalMessages: MutableList<VerbalMessage> = mutableListOf()
    suspend fun emitAi() {
        emit(ChatState.AwaitingAi(verbalMessages))
    }

    suspend fun emitUser() {
        emit(ChatState.AwaitingUser(verbalMessages))
    }

    var latestId: MessageId? = null

    fun pushMessage(message: Message) {
        val content = message.content.first() as? MessageContent.Text ?: return
        if (ChatRole.User == message.role || ChatRole.Assistant == message.role) {
            verbalMessages.add(
                VerbalMessage(
                    role = if (ChatRole.User == message.role) Role.USER else Role.ASSISTANT,
                    message = content.text.value,
                    Instant.fromEpochSeconds(message.createdAt.toLong())
                )
            )
        }
        latestId = message.id
    }
    fun pushUserMessage(text: String) {
        verbalMessages.add(
            VerbalMessage(
                role = Role.USER,
                message = text,
                clock.now()
            )
        )
    }

    fun isRunning(run: Run): Boolean {
        return listOf(Status.Queued, Status.InProgress, Status.Cancelling).indexOf(run.status) >= 0;
    }

    // Prefetch messages
    emitAi()
    val messages = openAi.messages(
        threadId = options.threadId,
        limit = options.maxRefetchMessages,
        order = SortOrder.Descending
    )

    for (message in messages) {
        pushMessage(message)
    }

    suspend fun runRun(run: Run) {
        lateinit var retrievedRun: Run

        do {
            delay(1500)
            retrievedRun = openAi.getRun(threadId = options.threadId, runId = run.id)
        } while (isRunning(retrievedRun))

        when(retrievedRun.status) {
            Status.Cancelled, Status.Expired, Status.Failed -> {
                emit(ChatState.Failed(verbalMessages))
                return
            }
            Status.Completed -> {
                val assistantMessages = openAi.messages(
                    threadId = options.threadId,
                    order = SortOrder.Ascending,
                    after = latestId
                )
                for (newMessage in assistantMessages) {
                    pushMessage(newMessage)
                }
            }
            Status.RequiresAction -> {
                val dispatcher = options.toolsDispatcher ?: return
                val action = (retrievedRun.requiredAction  as? RequiredAction.SubmitToolOutputs) ?: return
                val results = mutableListOf<ToolOutput>()
                for (toolCall in action.toolOutputs.toolCalls) {
                    if (toolCall !is ToolCall.Function) {
                        continue
                    }
                    results.add(
                        ToolOutput(
                            toolCall.id,
                            json.encodeToString(dispatcher.dispatch(toolCall.function.name, toolCall.function.argumentsAsJsonOrNull()))
                        )
                    )
                }
                if (results.isNotEmpty()) {
                    runRun(openAi.submitToolOutput(options.threadId, retrievedRun.id, results));
                }
            }
        }
    }

    while (currentCoroutineContext().isActive) {
        emitUser()
        val message = userChannel.receiveCatching().getOrNull() ?: break
        pushUserMessage(message)
        emitAi()

        val messageResult = openAi.message(
            threadId = options.threadId,
            request = MessageRequest(
                role = ChatRole.User,
                content = message
            )
        )
        latestId = messageResult.id

        runRun(openAi.createRun(
            options.threadId,
            request = RunRequest(
                assistantId = options.assistantId,
                instructions = message,
            )
        ))
    }
}

private val json : Json = Json {
    isLenient = true
}
