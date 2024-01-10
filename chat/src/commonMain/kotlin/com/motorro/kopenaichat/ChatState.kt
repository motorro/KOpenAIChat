package com.motorro.kopenaichat

import kotlinx.datetime.Instant

/**
 * Chat message role in verbal messages
 */
enum class Role {
    USER,
    ASSISTANT
}

/**
 * Verbal message to display to user
 */
data class VerbalMessage(val role: Role, val message: String, val timeStamp: Instant)

/**
 * Chat state that is transmitted through the flow
 */
sealed class ChatState {
    /**
     * A list of verbal messages so far
     */
    abstract val verbalMessages: List<VerbalMessage>

    /**
     * Awaiting answer from AI
     */
    data class AwaitingAi(override val verbalMessages: List<VerbalMessage>) : ChatState()

    /**
     * Awaiting answer from User
     */
    data class AwaitingUser(override val verbalMessages: List<VerbalMessage>) : ChatState()

    /**
     * Chat failed
     */
    data class Failed(override val verbalMessages: List<VerbalMessage>) : ChatState()
}