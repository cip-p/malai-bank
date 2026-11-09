package com.malai.accounts.util

object Levenshtein {

    fun distance(a: String, b: String): Int {
        val s = a.lowercase()
        val t = b.lowercase()
        if (s == t) return 0
        if (s.isEmpty()) return t.length
        if (t.isEmpty()) return s.length
        var prev = IntArray(t.length + 1) { it }
        var cur = IntArray(t.length + 1)
        for (i in 1..s.length) {
            cur[0] = i
            for (j in 1..t.length) {
                val cost = if (s[i - 1] == t[j - 1]) 0 else 1
                cur[j] = minOf(
                    cur[j - 1] + 1,
                    prev[j] + 1,
                    prev[j - 1] + cost,
                )
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[t.length]
    }
}
