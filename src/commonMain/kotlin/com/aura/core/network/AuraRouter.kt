package com.aura.core.network

import com.aura.core.BuildKonfig
import com.aura.core.classifier.AuraClassifier
import com.aura.core.classifier.RequestComplexity
import com.aura.core.classifier.RequestTopic
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuraRouter(private val client: AuraNetworkClient) {
    private val primaryEndpoint = "https://api.cerebras.ai/v1/chat/completions"
    private val primaryModel = "gpt-oss-120b"

    private val secondaryEndpoint = "https://api.groq.com/openai/v1/chat/completions"
    private val secondaryModel = "llama-3.3-70b-versatile"

    // lightweight micro-models to prefer for LIGHT complexity
    private val microEndpoint = "https://api.example-fast.ai/v1/chat/completions"
    private val microModel = "aura-micro-1.0"

    private val classifier = AuraClassifier()

    suspend fun streamChat(history: List<Message>, userPrompt: String): StreamBenchmarkResult {
        val messages = buildJsonArray {
            add(buildJsonObject {
                put("role", "system")
                put("content", "You are Aura, a fast and helpful assistant.")
            })
            for (message in history) {
                add(buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
            add(buildJsonObject {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val analysis = classifier.classify(userPrompt)

        // If LIGHT -> try micro-model first
        if (analysis.complexity == RequestComplexity.LIGHT) {
            val microPayload = buildJsonObject {
                put("model", microModel)
                put("stream", true)
                put("messages", messages)
            }.toString()

            try {
                val microResult = client.streamProvider(
                    endpoint = microEndpoint,
                    apiKey = BuildKonfig.CEREBRAS_API_KEY,
                    model = microModel,
                    extraHeaders = emptyMap(),
                    requestBody = microPayload
                )
                if (microResult.status == "OK") return microResult
            } catch (_: Throwable) {
                // fallthrough to heavier options
            }
        }

        // If HEAVY or micro failed, choose heavier models. If topic suggests code/math, prefer primary.
        val preferPrimary = when (analysis.topic) {
            RequestTopic.CODE, RequestTopic.MATH_LOGIC -> true
            else -> false
        }

        val primaryPayload = buildJsonObject {
            put("model", primaryModel)
            put("stream", true)
            put("messages", messages)
        }.toString()

        if (preferPrimary) {
            try {
                val primaryResult = client.streamProvider(
                    endpoint = primaryEndpoint,
                    apiKey = BuildKonfig.CEREBRAS_API_KEY,
                    model = primaryModel,
                    extraHeaders = emptyMap(),
                    requestBody = primaryPayload
                )
                if (primaryResult.status == "OK") return primaryResult
            } catch (_: Throwable) {
                // fallthrough
            }
        }

        val secondaryPayload = buildJsonObject {
            put("model", secondaryModel)
            put("stream", true)
            put("messages", messages)
        }.toString()

        return client.streamProvider(
            endpoint = secondaryEndpoint,
            apiKey = BuildKonfig.GROQ_API_KEY,
            model = secondaryModel,
            extraHeaders = emptyMap(),
            requestBody = secondaryPayload
        )
    }

    // Helper used for testing / decision inspection — does not perform network calls
    suspend fun selectModel(userPrompt: String): String {
        val analysis = classifier.classify(userPrompt)

        if (analysis.complexity == RequestComplexity.LIGHT) {
            return "MICRO (LIGHT) -> model=$microModel"
        }

        val preferPrimary = when (analysis.topic) {
            RequestTopic.CODE, RequestTopic.MATH_LOGIC -> true
            else -> false
        }

        return if (preferPrimary) {
            "PRIMARY (HEAVY) -> model=$primaryModel"
        } else {
            "SECONDARY (HEAVY) -> model=$secondaryModel"
        }
    }
}
