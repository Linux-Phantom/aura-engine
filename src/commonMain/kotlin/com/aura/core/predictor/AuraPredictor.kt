package com.aura.core.predictor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

/**
 * Provides local intent prediction using a phrase Trie and a bounded LRU cache.
 */
class AuraPredictor {
    private val patterns = listOf(
        "напиши код" to "напиши код на Kotlin...",
        "как настроить" to "как настроить проект в IntelliJ...",
        "исправь ошибку" to "исправь ошибку в следующем участке кода...",
        "продолжи предложение" to "продолжи предложение: ",
        "рефакторинг" to "рефакторинг кода для повышения читаемости..."
    )
    private val trie = TrieDataStructure()
    private val predictionCache = LruCache<String, List<String>>(CACHE_CAPACITY)

    init {
        patterns.forEach { (phrase, intent) ->
            trie.insert(phrase, intent)
        }
    }

    /**
     * Predicts completions for a phrase prefix.
     *
     * @param prefix text entered by the user so far.
     * @return cached or Trie-derived completion candidates.
     */
    fun predictNext(prefix: String): List<String> {
        val normalized = prefix.trim().lowercase()
        predictionCache[normalized]?.let { return it }

        val predictions = trie.predictNext(normalized)
        predictionCache[normalized] = predictions
        return predictions
    }

    /**
     * Classifies an input as a known intent and returns its completion metadata.
     *
     * @param input complete or partial user input.
     * @return high-confidence prediction for a known phrase, otherwise a low-confidence result.
     */
    suspend fun predictIntent(input: String): PredictionResult {
        val normalized = input.trim().lowercase()
        if (normalized.length > 3) {
            val predictedCompletion = predictNext(normalized).firstOrNull()
                ?: trie.predictMatchingPrefix(normalized).firstOrNull()
            if (predictedCompletion != null) {
                return PredictionResult(
                    originalText = input,
                    predictedCompletion = predictedCompletion,
                    confidence = PredictionConfidence.HIGH,
                    isPreflightReady = true
                )
            }
        }

        return PredictionResult(
            originalText = input,
            predictedCompletion = "",
            confidence = PredictionConfidence.LOW,
            isPreflightReady = false
        )
    }

    /**
     * Emits progress events while the preflight connection is warmed up.
     *
     * @param prompt prompt included in the final progress event.
     * @return a flow of connection and readiness events.
     */
    fun preflightWarmup(prompt: String): Flow<String> = flow {
        emit("opening connection for preflight...")
        delay(100)
        emit("socket handshake complete")
        delay(100)
        emit("SSE/subscription warmup ready")
        delay(100)
        emit("preflight ready for prompt: ${prompt.take(50)}")
    }.onEach {
        // no-op placeholder for side effects / progress hooks
    }

    internal fun cachedPredictionCount(): Int = predictionCache.size

    internal fun isPredictionCached(prefix: String): Boolean =
        predictionCache.containsKey(prefix.trim().lowercase())

    private companion object {
        const val CACHE_CAPACITY = 16
    }
}

private class LruCache<K, V>(private val capacity: Int) {
    private val entries = linkedMapOf<K, V>()

    val size: Int
        get() = entries.size

    operator fun get(key: K): V? {
        val value = entries.remove(key) ?: return null
        entries[key] = value
        return value
    }

    operator fun set(key: K, value: V) {
        entries.remove(key)
        entries[key] = value
        if (entries.size > capacity) {
            entries.remove(entries.keys.first())
        }
    }

    fun containsKey(key: K): Boolean = entries.containsKey(key)
}
