package com.aura.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.time.TimeSource

class AuraNetworkClient {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun getModels(endpoint: String, apiKey: String): String {
        return try {
            httpClient.get(endpoint) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $apiKey")
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                }
            }.body<String>()
        } catch (error: Throwable) {
            "Models request failed: ${error.message ?: error::class.simpleName ?: "unknown error"}"
        }
    }

    suspend fun streamProvider(
        endpoint: String,
        apiKey: String,
        model: String,
        extraHeaders: Map<String, String> = emptyMap(),
        requestBody: String? = null
    ): StreamBenchmarkResult {
        val startTime = TimeSource.Monotonic.markNow()
        val tokens = mutableListOf<String>()
        val collectedText = StringBuilder()
        var ttftMs: Long? = null

        return try {
            val payload = requestBody ?: buildJsonObject {
                put("model", model)
                put("stream", true)
                putJsonArray("messages") {
                    add(buildJsonObject {
                        put("role", "user")
                        put("content", "Hello")
                    })
                }
            }.toString()

            val responseText = httpClient.post(endpoint) {
                headers {
                    // allow explicit Authorization via extraHeaders (e.g., OpenRouter)
                    if (extraHeaders["Authorization"].isNullOrBlank()) {
                        append(HttpHeaders.Authorization, "Bearer $apiKey")
                    }
                    append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    append("Accept", "text/event-stream")
                    for ((name, value) in extraHeaders) {
                        append(name, value)
                    }
                }
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.bodyAsChannel().let { channel ->
                try {
                    while (!channel.isClosedForRead) {
                        val line = channel.readUTF8Line() ?: break
                        val eventData = parseSseData(line) ?: continue
                        val token = extractTokenFromSseData(eventData)
                        if (!token.isNullOrBlank()) {
                            if (ttftMs == null) {
                                ttftMs = startTime.elapsedNow().inWholeMilliseconds
                            }
                            tokens.add(token)
                            println(token)
                            collectedText.append(token)
                        }
                    }
                    collectedText.toString()
                } finally {
                    channel.cancel(cause = null)
                }
            }

            val totalMs = startTime.elapsedNow().inWholeMilliseconds
            StreamBenchmarkResult(
                ttftMs = ttftMs ?: totalMs,
                totalMs = totalMs,
                tokens = tokens,
                finalText = responseText,
                status = if (responseText.isBlank()) "ERR" else "OK"
            )
        } catch (error: Throwable) {
            val totalMs = startTime.elapsedNow().inWholeMilliseconds
            StreamBenchmarkResult(
                ttftMs = -1L,
                totalMs = totalMs,
                tokens = tokens,
                finalText = "",
                status = "ERR"
            )
        }
    }
}

internal fun parseSseData(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.startsWith("data:")) {
        val payload = trimmed.removePrefix("data:").trim()
        return if (payload == "[DONE]") null else payload
    }
    return null
}

internal fun extractTokenFromSseData(payload: String): String? {
    if (payload.isBlank()) return null
    return try {
        val element = Json.parseToJsonElement(payload)
        val choices = element.jsonObject["choices"]?.jsonArray ?: return null
        val delta = choices.firstOrNull()?.jsonObject?.get("delta")?.jsonObject ?: return null
        val content = delta["content"]?.jsonPrimitive?.content
        if (content.isNullOrBlank()) null else content
    } catch (_: Throwable) {
        null
    }
}

data class StreamBenchmarkResult(
    val ttftMs: Long,
    val totalMs: Long,
    val tokens: List<String>,
    val finalText: String,
    val status: String
)
