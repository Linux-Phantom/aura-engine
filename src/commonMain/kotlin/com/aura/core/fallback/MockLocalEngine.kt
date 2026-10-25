package com.aura.core.fallback

class MockLocalEngine : LocalModelEngine {
    override suspend fun generateLocalResponse(prompt: String): String {
        val normalized = prompt.trim().lowercase()
        return when {
            normalized.contains("привет") || normalized.contains("как дела") ->
                "Привет! Я работаю локально и готов ответить, даже если сеть недоступна."
            normalized.contains("код") || normalized.contains("кotlin") || normalized.contains("ktor") ->
                "Локальный движок может помочь с базовыми примерами кода. Попробуйте уточнить задачу."
            normalized.contains("ошибка") || normalized.contains("исправь") ->
                "Я могу дать локальную подсказку по устранению ошибки или уточнить проблему."
            else ->
                "Я сейчас в офлайн-режиме и помогу максимально возможным образом. Попробуйте переформулировать запрос."
        }
    }
}
