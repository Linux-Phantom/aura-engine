package com.aura.core.fallback

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuraFallbackRouterTest {
    @Test
    fun `routes to cloud when network is available`() = runBlocking {
        val localEngine = object : LocalModelEngine {
            override suspend fun generateLocalResponse(prompt: String): String = "local:$prompt"
        }

        val router = AuraFallbackRouter(localEngine)
        val response = router.executeWithFallback(
            prompt = "Contact user@example.com",
            isNetworkAvailable = true,
            remoteCall = { maskedPrompt ->
                assertTrue(maskedPrompt.contains("[EMAIL_1]"))
                "Cloud response for $maskedPrompt"
            }
        )

        assertFalse(response.isLocalFallback)
        assertEquals("Cloud response for Contact user@example.com", response.response)
    }

    @Test
    fun `switches to local fallback when network is unavailable`() = runBlocking {
        val localEngine = object : LocalModelEngine {
            override suspend fun generateLocalResponse(prompt: String): String = "local fallback for $prompt"
        }

        val router = AuraFallbackRouter(localEngine)
        val response = router.executeWithFallback(
            prompt = "Contact user@example.com",
            isNetworkAvailable = false,
            remoteCall = { "should not be called" }
        )

        assertTrue(response.isLocalFallback)
        assertEquals("local fallback for Contact user@example.com", response.response)
    }

    @Test
    fun `switches to local fallback when remote call throws`() = runBlocking {
        val localEngine = object : LocalModelEngine {
            override suspend fun generateLocalResponse(prompt: String): String = "fallback after error for $prompt"
        }

        val router = AuraFallbackRouter(localEngine)
        val response = router.executeWithFallback(
            prompt = "Contact user@example.com",
            isNetworkAvailable = true,
            remoteCall = { throw RuntimeException("network down") }
        )

        assertTrue(response.isLocalFallback)
        assertEquals("fallback after error for Contact user@example.com", response.response)
    }
}
