package com.aura.core.daemon

import com.aura.core.classifier.AuraClassifier
import com.aura.core.compressor.AuraContextCompressor
import com.aura.core.fallback.AuraFallbackRouter
import com.aura.core.fallback.LocalModelEngine
import com.aura.core.network.AuraNetworkClient
import com.aura.core.privacy.AuraPrivacyMasker
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.SocketTimeoutException
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Local JSON-lines service for other processes on the host. */
class AuraDaemon(
    private val socketPath: Path = Paths.get(
        System.getenv("AURA_SOCKET_PATH") ?: DEFAULT_SOCKET_PATH
    ),
    private val apiEndpoint: String = System.getenv("AURA_API_ENDPOINT") ?: DEFAULT_API_ENDPOINT,
    private val apiKey: String = System.getenv("AURA_API_KEY") ?: "",
    private val model: String = System.getenv("AURA_MODEL") ?: DEFAULT_MODEL,
    private val networkClient: AuraNetworkClient = AuraNetworkClient(),
    private val localEngine: LocalModelEngine = object : LocalModelEngine {
        override suspend fun generateLocalResponse(prompt: String): String =
            "Cloud unavailable; no local model backend is configured."
    },
    private val remoteCallOverride: (suspend (String) -> String)? = null
) : AutoCloseable {
    private val compressor = AuraContextCompressor()
    private val privacyMasker = AuraPrivacyMasker()
    private val classifier = AuraClassifier()
    private val fallbackRouter = AuraFallbackRouter(localEngine)
    private val remoteConfigured = remoteCallOverride != null || apiKey.isNotBlank()
    private val workers = ThreadPoolExecutor(
        WORKER_COUNT,
        WORKER_COUNT,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(QUEUED_CLIENTS),
        { task -> Thread(task, "aura-daemon-client").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()
    )

    @Volatile
    private var running = false

    @Volatile
    private var serverChannel: ServerSocketChannel? = null

    /** Binds the configured Unix socket and serves one JSON request per connection. */
    fun start() {
        check(!running) { "AuraDaemon is already running" }
        val parent = socketPath.parent ?: error("Socket path must have a parent directory")
        Files.createDirectories(parent)
        check(!Files.exists(socketPath, LinkOption.NOFOLLOW_LINKS)) {
            "Socket path already exists: $socketPath"
        }

        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        try {
            server.bind(UnixDomainSocketAddress.of(socketPath))
            Files.setPosixFilePermissions(
                socketPath,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_WRITE
                )
            )
            serverChannel = server
            running = true

            while (running) {
                val client = try {
                    server.accept()
                } catch (closed: ClosedChannelException) {
                    if (running) throw closed
                    break
                }

                try {
                    workers.execute { handleClient(client) }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    respond(client, errorResponse("server busy"))
                    client.close()
                }
            }
        } finally {
            running = false
            serverChannel = null
            server.close()
            workers.shutdown()
            if (!workers.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                workers.shutdownNow()
            }
            Files.deleteIfExists(socketPath)
        }
    }

    /** Processes the complete AURA pipeline and returns a structured JSON result. */
    internal suspend fun processPrompt(prompt: String): JsonObject {
        require(prompt.isNotBlank()) { "prompt must not be blank" }

        val maskingResult = privacyMasker.mask(prompt)
        val compressed = compressor.compress(maskingResult.maskedText)
        val analysis = classifier.classify(compressed.compressedText)
        val routed = fallbackRouter.executeWithFallback(
            prompt = compressed.compressedText,
            isNetworkAvailable = remoteConfigured,
            remoteCall = { maskedPrompt ->
                remoteCallOverride?.invoke(maskedPrompt) ?: callRemoteProvider(maskedPrompt)
            }
        )

        return buildJsonObject {
            put("ok", true)
            put("response", privacyMasker.unmask(routed.response, maskingResult.tokenMap))
            put("topic", analysis.topic.name)
            put("complexity", analysis.complexity.name)
            put("isLocalFallback", routed.isLocalFallback)
            put("compressedSavedRatioPercent", compressed.savedRatioPercent)
        }
    }

    override fun close() {
        running = false
        serverChannel?.close()
        workers.shutdownNow()
    }

    private suspend fun callRemoteProvider(maskedPrompt: String): String {
        val requestBody = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", maskedPrompt)
                })
            }
        }.toString()
        val result = networkClient.streamProvider(
            endpoint = apiEndpoint,
            apiKey = apiKey,
            model = model,
            requestBody = requestBody,
            onToken = {}
        )
        if (result.status != "OK") throw IOException("Remote provider request failed")
        return result.finalText
    }

    private fun handleClient(client: SocketChannel) {
        client.use { channel ->
            try {
                val requestLine = readRequestLine(channel)
                channel.configureBlocking(true)
                val prompt = parsePrompt(requestLine)
                val response = runBlocking { processPrompt(prompt) }
                writeLine(channel, response.toString())
            } catch (error: Exception) {
                System.err.println("[AuraDaemon] Request failed: ${error::class.simpleName}")
                try {
                    respond(channel, errorResponse("request could not be processed"))
                } catch (_: Exception) {
                    // The client may have disconnected before the response was written.
                }
            }
        }
    }

    private fun parsePrompt(requestLine: String): String {
        val prompt = Json.parseToJsonElement(requestLine)
            .jsonObject["prompt"]
            ?.jsonPrimitive
            ?.takeIf { it.isString }
            ?.content
            ?: throw IllegalArgumentException("request must contain a string prompt")
        require(prompt.isNotBlank()) { "prompt must not be blank" }
        return prompt
    }

    private fun readRequestLine(channel: SocketChannel): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocate(READ_BUFFER_BYTES)
        channel.configureBlocking(false)

        Selector.open().use { selector ->
            channel.register(selector, SelectionKey.OP_READ)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_TIMEOUT_MILLIS)

            while (true) {
                val remainingNanos = deadline - System.nanoTime()
                if (remainingNanos <= 0L) throw SocketTimeoutException("request read timed out")
                val waitMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos)
                    .coerceIn(1L, Int.MAX_VALUE.toLong())
                if (selector.select(waitMillis) == 0) continue
                selector.selectedKeys().clear()

                buffer.clear()
                val count = channel.read(buffer)
                if (count < 0) break
                buffer.flip()
                while (buffer.hasRemaining()) {
                    val byte = buffer.get()
                    if (byte == '\n'.code.toByte()) {
                        return output.toString(StandardCharsets.UTF_8).trimEnd('\r')
                    }
                    if (output.size() >= MAX_REQUEST_BYTES) {
                        throw IOException("request exceeds the maximum size")
                    }
                    output.write(byte.toInt())
                }
            }
        }

        if (output.size() == 0) throw IOException("empty request")
        return output.toString(StandardCharsets.UTF_8).trimEnd('\r')
    }

    private fun respond(channel: SocketChannel, response: String) {
        channel.configureBlocking(true)
        writeLine(channel, response)
    }

    private fun writeLine(channel: SocketChannel, response: String) {
        val buffer = ByteBuffer.wrap((response + "\n").toByteArray(StandardCharsets.UTF_8))
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    private fun errorResponse(message: String): String = buildJsonObject {
        put("ok", false)
        put("error", message)
    }.toString()

    private companion object {
        const val DEFAULT_SOCKET_PATH = "/run/aura-engine/aura-engine.sock"
        const val DEFAULT_API_ENDPOINT = "https://api.cerebras.ai/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-oss-120b"
        const val MAX_REQUEST_BYTES = 64 * 1024
        const val READ_BUFFER_BYTES = 4096
        const val READ_TIMEOUT_MILLIS = 30_000L
        const val WORKER_COUNT = 4
        const val QUEUED_CLIENTS = 32
        const val SHUTDOWN_TIMEOUT_SECONDS = 5L
    }
}

fun main() {
    val daemon = AuraDaemon()
    Runtime.getRuntime().addShutdownHook(
        Thread({ daemon.close() }, "aura-daemon-shutdown")
    )
    daemon.start()
}