package com.aura.core.network

import kotlinx.coroutines.runBlocking

fun main() {
    val client = AuraNetworkClient()
    val router = AuraRouter(client)
    val conversationManager = ConversationManager()

    println("Aura CLI Chat — type 'exit' to quit.")
    while (true) {
        print("> ")
        val line = readLine() ?: break
        val input = line.trim()
        if (input.lowercase() == "exit") break
        if (input.isEmpty()) continue

        runBlocking {
            val result = router.streamChat(conversationManager.getRecentHistory(), input)
            println() // newline after streaming output
            conversationManager.addMessage("user", input)
            if (result.status == "OK") {
                conversationManager.addMessage("assistant", result.finalText)
            } else {
                println("[Aura] Error: provider failed to produce a response.")
            }
        }
    }

    println("Goodbye.")
}
