package com.aura.core.predictor

/**
 * Prefix index for mapping phrases to intent completions.
 *
 * Phrase insertion is O(L), and prefix lookup is O(L + K), where L is the
 * prefix length and K is the number of returned intents.
 */
class TrieDataStructure {
    private class Node {
        val children = linkedMapOf<Char, Node>()
        var intent: String? = null
    }

    private val root = Node()

    /**
     * Adds or replaces the intent associated with [phrase].
     *
     * @param phrase phrase to index.
     * @param intent completion or intent value returned for the phrase.
     */
    fun insert(phrase: String, intent: String) {
        var node = root
        phrase.forEach { character ->
            node = node.children.getOrPut(character) { Node() }
        }
        node.intent = intent
    }

    /**
     * Returns all intents below [prefix] in insertion order.
     *
     * @param prefix phrase prefix to search.
     * @return matching intents, or an empty list when the prefix is unknown.
     */
    fun predictNext(prefix: String): List<String> {
        var node = root
        prefix.forEach { character ->
            node = node.children[character] ?: return emptyList()
        }

        val predictions = ArrayList<String>()
        collectIntents(node, predictions)
        return predictions
    }

    /**
     * Returns intents whose complete phrases are prefixes of [input].
     *
     * @param input input text that may extend a registered phrase.
     * @return matching intents encountered while walking [input].
     */
    fun predictMatchingPrefix(input: String): List<String> {
        var node = root
        val predictions = ArrayList<String>()
        input.forEach { character ->
            node = node.children[character] ?: return predictions
            node.intent?.let(predictions::add)
        }
        return predictions
    }

    private fun collectIntents(node: Node, predictions: MutableList<String>) {
        node.intent?.let(predictions::add)
        node.children.values.forEach { child ->
            collectIntents(child, predictions)
        }
    }
}