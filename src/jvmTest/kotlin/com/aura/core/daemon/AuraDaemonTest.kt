package com.aura.core.daemon

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuraDaemonTest {
    @Test
    fun `processes prompt through privacy compression classification and cloud routing`() = runBlocking {
        var remotePrompt = ""
        val daemon = AuraDaemon(
            remoteCallOverride = { prompt ->
                remotePrompt = prompt
                "Processed $prompt"
            }
        )

        val result = daemon.processPrompt(
            "Please email user@example.com!!!   can   you   help???"
        )

        assertFalse(remotePrompt.contains("user@example.com"))
        assertTrue(remotePrompt.contains("[EMAIL_1]"))
        assertEquals(
            "Processed email user@example.com!  help?",
            result["response"]?.jsonPrimitive?.content
        )
        assertEquals("GENERAL_CHAT", result["topic"]?.jsonPrimitive?.content)
        assertEquals("LIGHT", result["complexity"]?.jsonPrimitive?.content)
        assertEquals("false", result["isLocalFallback"]?.jsonPrimitive?.content)
        assertTrue((result["compressedSavedRatioPercent"]?.jsonPrimitive?.content?.toInt() ?: 0) > 0)
    }

    @Test
    fun `uses local fallback when cloud credentials are not configured`() = runBlocking {
        val daemon = AuraDaemon(apiKey = "")

        val result = daemon.processPrompt("A short local request")

        assertEquals("true", result["isLocalFallback"]?.jsonPrimitive?.content)
        assertTrue(result["response"]?.jsonPrimitive?.content.orEmpty().contains("no local model backend"))
    }

    @Test
    fun `serves a JSON prompt over a Unix domain socket`() {
        val directory = Files.createTempDirectory("aura-daemon-test")
        val socketPath = directory.resolve("aura.sock")
        val daemon = AuraDaemon(
            socketPath = socketPath,
            remoteCallOverride = { prompt -> "Cloud: $prompt" }
        )
        val daemonFailure = AtomicReference<Throwable?>()
        val daemonThread = Thread({
            try {
                daemon.start()
            } catch (error: Throwable) {
                daemonFailure.set(error)
            }
        })
        daemonThread.start()

        try {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!Files.exists(socketPath) && daemonThread.isAlive && System.nanoTime() < deadline) {
                Thread.yield()
            }
            assertTrue(Files.exists(socketPath), "daemon did not create its socket")

            SocketChannel.open(StandardProtocolFamily.UNIX).use { client ->
                client.connect(UnixDomainSocketAddress.of(socketPath))
                val request = buildJsonObject { put("prompt", "hello") }.toString() + "\n"
                val requestBuffer = ByteBuffer.wrap(request.toByteArray(StandardCharsets.UTF_8))
                while (requestBuffer.hasRemaining()) client.write(requestBuffer)

                val responseBytes = ByteArrayOutputStream()
                val responseBuffer = ByteBuffer.allocate(1024)
                var responseComplete = false
                while (!responseComplete) {
                    responseBuffer.clear()
                    val count = client.read(responseBuffer)
                    if (count < 0) break
                    responseBuffer.flip()
                    while (responseBuffer.hasRemaining()) {
                        val byte = responseBuffer.get()
                        if (byte == '\n'.code.toByte()) {
                            responseComplete = true
                            break
                        }
                        responseBytes.write(byte.toInt())
                    }
                }

                val response = Json.parseToJsonElement(
                    responseBytes.toString(StandardCharsets.UTF_8)
                ).jsonObject
                assertEquals("Cloud: hello", response["response"]?.jsonPrimitive?.content)
                assertEquals("false", response["isLocalFallback"]?.jsonPrimitive?.content)
            }
        } finally {
            daemon.close()
            daemonThread.join(5_000)
            Files.deleteIfExists(socketPath)
            Files.deleteIfExists(directory)
        }

        assertTrue(!daemonThread.isAlive, "daemon did not stop")
        daemonFailure.get()?.let { throw AssertionError("daemon failed", it) }
    }
}