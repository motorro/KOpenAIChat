package com.motorro.kopenaichat.examples

import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.model.ModelId
import com.motorro.kopenaichat.ChatOptions
import com.motorro.kopenaichat.ChatState
import com.motorro.kopenaichat.ChatTools
import com.motorro.kopenaichat.ToolsDispatcher
import com.motorro.kopenaichat.chatConversation
import kotlinx.coroutines.Dispatchers
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

    runBlocking(Dispatchers.Default) {
        val channel = Channel<String>()

        chatConversation(token, options, channel)
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
                        if ("quit" == input?.lowercase()) {
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

private val options = ChatOptions(
    ModelId("gpt-3.5-turbo-1106"),
    systemMessage = """
                    You are a calculator.
                    Use supplied functions to add and subtract numbers
                """.trimIndent(),
    logging = false,
    tools = object : ChatTools {
        override val tools: List<Tool> = listOf(
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
        override val dispatcher = object : ToolsDispatcher {
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
    }
)
