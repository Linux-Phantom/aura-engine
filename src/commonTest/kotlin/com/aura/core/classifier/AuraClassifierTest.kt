package com.aura.core.classifier

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AuraClassifierTest {
    @Test
    fun `classifies simple general chat requests as light`() = runBlocking {
        val result = AuraClassifier().classify("Hello there, how are you?")

        assertEquals(RequestTopic.GENERAL_CHAT, result.topic)
        assertEquals(RequestComplexity.LIGHT, result.complexity)
    }

    @Test
    fun `classifies code-oriented requests as heavy and code`() = runBlocking {
        val result = AuraClassifier().classify("Fix a Kotlin bug in this class and explain the stacktrace")

        assertEquals(RequestTopic.CODE, result.topic)
        assertEquals(RequestComplexity.HEAVY, result.complexity)
    }

    @Test
    fun `classifies long prompts as heavy general chat`() = runBlocking {
        val longPrompt = buildString {
            append("This is a long request. ")
            repeat(20) {
                append("Please help me analyze the context and provide a detailed answer. ")
            }
        }

        val result = AuraClassifier().classify(longPrompt)

        assertEquals(RequestTopic.GENERAL_CHAT, result.topic)
        assertEquals(RequestComplexity.HEAVY, result.complexity)
    }
}
