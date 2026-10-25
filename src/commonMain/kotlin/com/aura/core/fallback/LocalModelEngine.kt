package com.aura.core.fallback

/** Provides local response generation for offline or degraded execution. */
interface LocalModelEngine {
    /**
     * Generates a response for a prompt.
     *
     * @param prompt prompt passed to the local model.
     * @return generated local response text.
     */
    suspend fun generateLocalResponse(prompt: String): String
}
