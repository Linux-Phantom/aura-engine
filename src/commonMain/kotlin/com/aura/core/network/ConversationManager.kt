package com.aura.core.network

data class Message(
    val role: String,
    val content: String
)

class ConversationManager {
    private val messages = mutableListOf<Message>()

    fun addMessage(role: String, content: String) {
        messages.add(Message(role = role, content = content))
    }

    fun getRecentHistory(limit: Int = 10): List<Message> {
        return messages.takeLast(limit.coerceAtLeast(0))
    }
}
