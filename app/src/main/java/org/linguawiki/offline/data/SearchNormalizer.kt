package org.linguawiki.offline.data

import java.text.Normalizer
import java.util.Locale

object SearchNormalizer {
    private val whitespace = Regex("\\s+")
    private val combiningMarks = Regex("\\p{Mn}+")

    fun canonical(value: String): String = Normalizer.normalize(
        value.trim().replace(whitespace, " "),
        Normalizer.Form.NFC,
    ).lowercase(Locale.ROOT)

    fun fold(value: String): String {
        val canonical = canonical(value)
            .replace('ł', 'l')
            .replace('đ', 'd')
            .replace("ß", "ss")
            .replace("æ", "ae")
            .replace("œ", "oe")
        return Normalizer.normalize(canonical, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
    }

    fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitution,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
