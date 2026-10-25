package com.aura.core.predictor

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuraPredictorTest {
    @Test
    fun `trie returns expected intents for a prefix`() {
        val trie = TrieDataStructure()
        trie.insert("напиши код", "code")
        trie.insert("напиши тест", "test")
        trie.insert("прочитай файл", "read")

        assertEquals(listOf("code", "test"), trie.predictNext("напиши"))
        assertEquals(emptyList(), trie.predictNext("удали"))
    }

    @Test
    fun `predictor caches predictions and evicts oldest entries after sixteen keys`() {
        val predictor = AuraPredictor()

        repeat(16) { index ->
            predictor.predictNext("unknown-$index")
        }

        assertEquals(16, predictor.cachedPredictionCount())
        assertTrue(predictor.isPredictionCached("unknown-15"))
        assertTrue(predictor.isPredictionCached("unknown-0"))

        val knownPrediction = predictor.predictNext("напиши")
        assertEquals(listOf("напиши код на Kotlin..."), knownPrediction)
        assertEquals(16, predictor.cachedPredictionCount())
        assertTrue(predictor.isPredictionCached("напиши"))
        assertTrue(!predictor.isPredictionCached("unknown-0"))
    }

    @Test
    fun `predictIntent returns preflight ready for known phrase`() = runBlocking {
        val predictor = AuraPredictor()

        val result = predictor.predictIntent("напиши код на Kotlin")

        assertTrue(result.isPreflightReady)
        assertEquals("напиши код на Kotlin", result.originalText)
        assertEquals("напиши код на Kotlin...", result.predictedCompletion)
    }
}