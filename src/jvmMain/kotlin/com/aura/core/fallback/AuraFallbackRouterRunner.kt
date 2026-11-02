package com.aura.core.fallback

import kotlinx.coroutines.runBlocking

fun main() {
    val localEngine = MockLocalEngine()
    val router = AuraFallbackRouter(localEngine)

    runBlocking {
        val prompt = "Напиши пример кода на Kotlin для Ktor"
        val result1 = router.executeWithFallback(prompt, isNetworkAvailable = false) { _ ->
            error("This should not run when offline")
        }
        println("Offline fallback result: $result1")

        val result2 = router.executeWithFallback(prompt, isNetworkAvailable = true) { _ ->
            throw RuntimeException("Simulated remote failure")
        }
        println("Remote failure fallback result: $result2")
    }
}
