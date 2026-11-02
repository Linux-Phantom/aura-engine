package com.aura.core.network

import kotlinx.coroutines.runBlocking

fun main() {
    val client = AuraNetworkClient()
    val router = AuraRouter(client)

    val prompts = listOf(
        "Привет! Как дела?",
        "Напиши функцию на Kotlin для асинхронного потокового чтения данных через Ktor и Flow."
    )

    runBlocking {
        for ((i, p) in prompts.withIndex()) {
            val decision = router.selectModel(p)
            println("Prompt ${i + 1}: '$p'")
            println("Router decision: $decision")
            println()
        }
    }
}
