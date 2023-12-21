package com.motorro.kopenaichat.examples

import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.assistant.AssistantRequest
import com.aallam.openai.api.assistant.AssistantTool
import com.aallam.openai.api.assistant.Function
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.motorro.kopenaichat.AssistantOptions
import com.motorro.kopenaichat.ChatOptions
import com.motorro.kopenaichat.ChatState
import com.motorro.kopenaichat.ChatTools
import com.motorro.kopenaichat.ToolsDispatcher
import com.motorro.kopenaichat.assistantConversation
import com.motorro.kopenaichat.chatConversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun main() {
    println("Starting...")
    val token = System.getenv("OPENAI_API_KEY")

    println("Choose: chat or assist")
    lateinit var choice: String
    do {
        choice = readlnOrNull().orEmpty()
    } while ("chat" != choice && "assist" != choice)

    runBlocking(Dispatchers.Default) {
        if ("chat" == choice) {
            chat(token)
        } else {
            assist(token)
        }
        println("Complete")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
suspend fun CoroutineScope.chat(token: String) {
    val channel = Channel<String>()

    chatConversation(token, chatOptions, channel)
        .cancellable()
        .onEach {
            println(it.javaClass.simpleName)
            it.verbalMessages.lastOrNull()?.let { println("${it.role}: ${it.message}") }
        }
        .flowOn(Dispatchers.IO)
        .filter { it is ChatState.AwaitingUser }
        .flatMapLatest {
            flow<String> {
                var input = ""
                while (true) {
                    input = readlnOrNull().orEmpty()
                    if (input.isEmpty()) {
                        println("Please enter something")
                        continue
                    }
                    if ("quit" == input.lowercase()) {
                        println("Bye!")
                    }
                    break
                }
                emit(input)
            }
        }
        .takeWhile { "quit" != it }
        .onEach {
            channel.send(it)
        }
        .onCompletion {
            channel.close()
            println("Complete")
        }
        .launchIn(this)
}

@OptIn(ExperimentalCoroutinesApi::class, BetaOpenAI::class)
suspend fun CoroutineScope.assist(token: String) {
    val channel = Channel<String>()

    val openAi = OpenAI(
        OpenAIConfig(
            token = token,
            logging = LoggingConfig(
                logLevel = when (chatOptions.logging) {
                    true -> LogLevel.Info
                    else -> LogLevel.None
                }
            )
        )
    )

    val assistant = openAi.assistant(
        request = AssistantRequest(
            name = "Math Tutor",
            instructions = """
                You are a calculator.
                Use supplied functions to add and subtract numbers
            """.trimIndent(),
            tools = assistantTools,
            model = ModelId("gpt-4-1106-preview")
        )
    )
    val thread = openAi.thread()

    assistantConversation(openAi, AssistantOptions(assistant.id, thread.id, toolsDispatcher = toolsDispatcher), channel)
        .cancellable()
        .onEach { state ->
            println(state.javaClass.simpleName)
            state.verbalMessages.lastOrNull()?.let { println("${it.role}: ${it.message}") }
        }
        .flowOn(Dispatchers.IO)
        .filter { it is ChatState.AwaitingUser }
        .flatMapLatest {
            flow<String> {
                var input = ""
                while (true) {
                    input = readlnOrNull().orEmpty()
                    if (input.isEmpty()) {
                        println("Please enter something")
                        continue
                    }
                    if ("quit" == input.lowercase()) {
                        println("Bye!")
                    }
                    break
                }
                emit(input)
            }
        }
        .takeWhile { "quit" != it }
        .onEach {
            channel.send(it)
        }
        .onCompletion {
            channel.close()
            println("Deleting thread and assistant...")
            openAi.delete(thread.id)
            openAi.delete(assistant.id)
            println("Complete")
        }
        .launchIn(this)
}

private val json = Json {
    isLenient = true
}

private fun add(a: Int, b: Int): Int {
    println("FUNCTION: Adding $a to $b")
    return a + b
}

private fun subtract(a: Int, b: Int): Int {
    println("FUNCTION: Subtracting $b from $a")
    return a - b
}

private val chatTools = object : ChatTools {
    override val tools get() = toolsDescription
    override val dispatcher get() = toolsDispatcher
}

private val toolsDispatcher = object : ToolsDispatcher {
    override suspend fun dispatch(name: String, args: JsonObject?): JsonElement = when(name) {
        "add" -> json.encodeToJsonElement(Int.serializer(), add(
            args!!.getValue("a").jsonPrimitive.int,
            args.getValue("b").jsonPrimitive.int
        ))
        "subtract" -> json.encodeToJsonElement(Int.serializer(), subtract(
            args!!.getValue("a").jsonPrimitive.int,
            args.getValue("b").jsonPrimitive.int
        ))
        else -> error("Unknown function $name")
    }
}

private val toolsDescription: List<Tool> = listOf(
    Tool.function(
        "add",
        "Use this function to add two numbers. Use return value as a result",
        Parameters.buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("a") {
                    put("type", "number")
                    put("description", "First additive")
                }
                putJsonObject("b") {
                    put("type", "number")
                    put("description", "Second additive")
                }
            }
            putJsonArray("required") {
                add("a")
                add("b")
            }
        }
    ),
    Tool.function(
        "subtract",
        "Use this function to subtract one number from another. Use return value as a result",
        Parameters.buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("a") {
                    put("type", "number")
                    put("description", "minuend")
                }
                putJsonObject("b") {
                    put("type", "number")
                    put("description", "subtrahend")
                }
            }
            putJsonArray("required") {
                add("a")
                add("b")
            }
        }
    )
)

@OptIn(BetaOpenAI::class)
private val assistantTools: List<AssistantTool> = listOf(
    AssistantTool.FunctionTool(
        Function(
            "add",
            "Use this function to add two numbers. Use return value as a result",
            Parameters.buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("a") {
                        put("type", "number")
                        put("description", "First additive")
                    }
                    putJsonObject("b") {
                        put("type", "number")
                        put("description", "Second additive")
                    }
                }
                putJsonArray("required") {
                    add("a")
                    add("b")
                }
            }
        ),
    ),
    AssistantTool.FunctionTool(
        Function(
            "subtract",
            "Use this function to subtract one number from another. Use return value as a result",
            Parameters.buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("a") {
                        put("type", "number")
                        put("description", "minuend")
                    }
                    putJsonObject("b") {
                        put("type", "number")
                        put("description", "subtrahend")
                    }
                }
                putJsonArray("required") {
                    add("a")
                    add("b")
                }
            }
        )
    )
)

private val chatOptions = ChatOptions(
    ModelId("gpt-3.5-turbo-1106"),
    systemMessage = """
        You are a calculator.
        Use supplied functions to add and subtract numbers
    """.trimIndent(),
    logging = false,
    tools = chatTools
)


