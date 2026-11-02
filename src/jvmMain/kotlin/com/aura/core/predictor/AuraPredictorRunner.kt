package com.aura.core.predictor

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

fun main() {
    val predictor = AuraPredictor()
    val input = "напиши код"

    runBlocking {
        val result = predictor.predictIntent(input)
        println("PredictionResult: $result")
        predictor.preflightWarmup(input).collect { event ->
            println("Preflight event: $event")
        }
    }
}
