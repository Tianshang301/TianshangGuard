package com.tianshang.guard.core.dns

/**
 * BK-tree for efficient Levenshtein distance search.
 * 
 * A BK-tree is a metric tree specifically adapted for discrete metric spaces.
 * It's used to efficiently find all elements within a given distance of a query element.
 * 
 * Time complexity: O(log n) average case for searches
 * Space complexity: O(n)
 */
class BkTree(private val threshold: Int = 3) {

    private var root: Node? = null
    private var size = 0

    private data class Node(
        val word: String,
        val children: MutableMap<Int, Node> = mutableMapOf()
    )

    /**
     * Insert a word into the BK-tree.
     */
    fun insert(word: String) {
        if (root == null) {
            root = Node(word)
            size++
            return
        }

        var current = root!!
        while (true) {
            val distance = levenshteinDistance(word, current.word)
            if (distance == 0) return // Duplicate, skip

            val child = current.children[distance]
            if (child == null) {
                current.children[distance] = Node(word)
                size++
                return
            }
            current = child
        }
    }

    /**
     * Find all words within the threshold distance of the query.
     */
    fun search(query: String): List<String> {
        if (root == null) return emptyList()
        return searchNode(root!!, query)
    }

    /**
     * Check if any word exists within the threshold distance of the query.
     */
    fun hasNearMatch(query: String): Boolean {
        if (root == null) return false
        return hasNearMatchNode(root!!, query)
    }

    /**
     * Get the closest match to the query within the threshold.
     */
    fun findClosest(query: String): Pair<String, Int>? {
        if (root == null) return null
        var bestMatch: Pair<String, Int>? = null
        searchNodeWithCallback(root!!, query) { word, distance ->
            if (bestMatch == null || distance < bestMatch!!.second) {
                bestMatch = Pair(word, distance)
            }
        }
        return bestMatch
    }

    private fun searchNode(node: Node, query: String): List<String> {
        val results = mutableListOf<String>()
        searchNodeWithCallback(node, query) { word, _ ->
            results.add(word)
        }
        return results
    }

    private fun searchNodeWithCallback(
        node: Node,
        query: String,
        callback: (String, Int) -> Unit
    ) {
        val distance = levenshteinDistance(query, node.word)

        // If within threshold, add to results
        if (distance <= threshold) {
            callback(node.word, distance)
        }

        // Search children where |distance - childDist| <= threshold
        val minDist = distance - threshold
        val maxDist = distance + threshold

        for ((childDist, child) in node.children) {
            if (childDist in minDist..maxDist) {
                searchNodeWithCallback(child, query, callback)
            }
        }
    }

    private fun hasNearMatchNode(node: Node, query: String): Boolean {
        val distance = levenshteinDistance(query, node.word)
        if (distance <= threshold) return true

        val minDist = distance - threshold
        val maxDist = distance + threshold

        for ((childDist, child) in node.children) {
            if (childDist in minDist..maxDist) {
                if (hasNearMatchNode(child, query)) return true
            }
        }
        return false
    }

    /**
     * Compute Levenshtein distance between two strings.
     */
    private fun levenshteinDistance(a: String, b: String): Int {
        val costs = IntArray(b.length + 1)
        for (j in 0..b.length) costs[j] = j
        for (i in 1..a.length) {
            costs[0] = i
            var previous = i - 1
            for (j in 1..b.length) {
                val current = previous
                previous = costs[j]
                costs[j] = minOf(
                    costs[j] + 1,
                    costs[j - 1] + 1,
                    current + if (a[i - 1] != b[j - 1]) 1 else 0
                )
            }
        }
        return costs[b.length]
    }

    fun getSize(): Int = size
}
