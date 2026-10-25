package com.aura.core.network

import com.aura.core.BuildKonfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

fun main() {
    runBlocking {
        val client = AuraNetworkClient()

        val providers = listOf(
            ProviderBenchmark(
                name = "Cerebras-gemma-4-31b",
                endpoint = "https://api.cerebras.ai/v1/chat/completions",
                model = "gemma-4-31b",
                apiKey = BuildKonfig.CEREBRAS_API_KEY
            ),
            ProviderBenchmark(
                name = "Cerebras-zai-glm-4.7",
                endpoint = "https://api.cerebras.ai/v1/chat/completions",
                model = "zai-glm-4.7",
                apiKey = BuildKonfig.CEREBRAS_API_KEY
            ),
            ProviderBenchmark(
                name = "Cerebras-gpt-oss-120b",
                endpoint = "https://api.cerebras.ai/v1/chat/completions",
                model = "gpt-oss-120b",
                apiKey = BuildKonfig.CEREBRAS_API_KEY
            ),
            ProviderBenchmark(
                name = "Groq-llama-3.3-70b-versatile",
                endpoint = "https://api.groq.com/openai/v1/chat/completions",
                model = "llama-3.3-70b-versatile",
                apiKey = BuildKonfig.GROQ_API_KEY
            )
            ,
            ProviderBenchmark(
                name = "OpenRouter-deepseek-r1",
                endpoint = "https://openrouter.ai/api/v1/chat/completions",
                model = "deepseek/deepseek-r1:free",
                apiKey = BuildKonfig.OPENROUTER_API_KEY,
                extraHeaders = mapOf(
                    "Authorization" to "Bearer ${BuildKonfig.OPENROUTER_API_KEY}",
                    "HTTP-Referer" to "https://aura.ai",
                    "X-Title" to "Aura Engine"
                )
            )
        )

        val results = providers.map { provider ->
            async {
                val result = client.streamProvider(
                    endpoint = provider.endpoint,
                    apiKey = provider.apiKey,
                    model = provider.model,
                    extraHeaders = provider.extraHeaders,
                    requestBody = provider.requestBody
                )
                provider to result
            }
        }.awaitAll()

        println("Streaming Benchmark Leaderboard")
        println("-".repeat(112))
        println(String.format("%-24s | %-7s | %-10s | %-10s | %s", "Provider", "Status", "TTFT (ms)", "Total (ms)", "Tokens"))
        println("-".repeat(112))

        for ((provider, result) in results) {
            val snippet = result.finalText
                .replace("\n", " ")
                .replace("\t", " ")
                .take(80)
            println(String.format("%-24s | %-7s | %-10s | %-10s | %s", provider.name, result.status, result.ttftMs, result.totalMs, snippet))
        }
    }
}

data class ProviderBenchmark(
    val name: String,
    val endpoint: String,
    val model: String,
    val apiKey: String,
    val extraHeaders: Map<String, String> = emptyMap(),
    val requestBody: String? = null
)
