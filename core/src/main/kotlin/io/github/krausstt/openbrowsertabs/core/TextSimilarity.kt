package io.github.krausstt.openbrowsertabs.core

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * TF-IDF bag-of-words cosine similarity — the license-free, dependency-free
 * v1 of the association layer ("hängt zusammen mit …"). Deliberately behind
 * a tiny API so an ONNX embedding model (granite-278m, Apache-2.0) can
 * replace the scoring internals in a later build without touching callers.
 */
object TextSimilarity {

    data class Doc(val id: Long, val text: String)
    data class Related(val id: Long, val score: Double)

    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{N}]+")

    // minimal DE+EN stopwords; URLs/slugs contribute most signal anyway
    private val STOPWORDS = setOf(
        "der", "die", "das", "und", "oder", "ein", "eine", "einen", "einem",
        "mit", "für", "von", "auf", "ist", "sind", "wird", "werden", "nicht",
        "auch", "als", "bei", "sich", "den", "dem", "des", "aus", "nach",
        "über", "wie", "was", "wir", "ich", "sie", "aber", "noch", "schon",
        "the", "and", "for", "with", "that", "this", "from", "have", "has",
        "are", "was", "were", "will", "can", "you", "your", "its", "our",
        "not", "but", "all", "how", "why", "what", "when", "more", "most",
        "into", "than", "then", "them", "they", "there", "here", "about",
        "www", "http", "https", "com", "org",
    )

    fun tokenize(text: String): List<String> =
        TOKEN_SPLIT.split(text.lowercase())
            .filter { it.length >= 3 && it !in STOPWORDS }

    /**
     * Top-[k] most similar docs to [targetId] within [docs].
     * Scores below [minScore] are noise and dropped — an empty result is a
     * valid answer ("nichts Verwandtes"), better than fake associations.
     */
    fun topRelated(
        docs: List<Doc>,
        targetId: Long,
        k: Int = 3,
        minScore: Double = 0.08,
    ): List<Related> {
        if (docs.size < 2) return emptyList()
        val tokenized = docs.associate { it.id to tokenize(it.text) }
        val target = tokenized[targetId] ?: return emptyList()
        if (target.isEmpty()) return emptyList()

        val n = docs.size
        val df = HashMap<String, Int>()
        tokenized.values.forEach { tokens ->
            tokens.toSet().forEach { df[it] = (df[it] ?: 0) + 1 }
        }
        fun idf(term: String) = ln((n + 1.0) / ((df[term] ?: 0) + 1.0)) + 1.0

        fun vectorOf(tokens: List<String>): Map<String, Double> {
            val tf = tokens.groupingBy { it }.eachCount()
            val v = tf.mapValues { (term, count) -> count * idf(term) }
            val norm = sqrt(v.values.sumOf { it * it })
            return if (norm == 0.0) emptyMap() else v.mapValues { it.value / norm }
        }

        val targetVec = vectorOf(target)
        return docs.asSequence()
            .filter { it.id != targetId }
            .map { doc ->
                val vec = vectorOf(tokenized[doc.id].orEmpty())
                // iterate the smaller map for the dot product
                val (small, large) = if (targetVec.size <= vec.size) targetVec to vec else vec to targetVec
                Related(doc.id, small.entries.sumOf { (t, w) -> w * (large[t] ?: 0.0) })
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(k)
            .toList()
    }
}
