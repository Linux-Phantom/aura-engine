package com.aura.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationManagerTest {
    @Test
    fun `returns the most recent messages in order`() {
        val manager = ConversationManager()
        manager.addMessage("user", "Hello")
        manager.addMessage("assistant", "Hi there")
        manager.addMessage("user", "How are you?")

        val recent = manager.getRecentHistory(2)

        assertEquals(
            listOf(
                Message("assistant", "Hi there"),
                Message("user", "How are you?")
            ),
            recent
        )
    }
}
