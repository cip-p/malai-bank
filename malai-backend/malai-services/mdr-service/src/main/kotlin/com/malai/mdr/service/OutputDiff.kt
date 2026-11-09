package com.malai.mdr.service

import com.malai.mdr.model.MdrDiffLine

// Line-level difference between two immutable outputs, derived at read time for an EDIT ruling
// (the Raw output vs the human's edited output). A longest-common-subsequence walk yields the
// shortest sequence of UNCHANGED / REMOVED / ADDED lines. Pure and deterministic: the same two
// inputs always yield the same diff, so nothing about it needs to be stored -- it can never drift
// from the artifacts it describes, which is the whole reason the diff is never persisted.
object OutputDiff {

    fun between(before: String, after: String): List<MdrDiffLine> {
        val a = before.split("\n")
        val b = after.split("\n")
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1
                else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val lines = mutableListOf<MdrDiffLine>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> {
                    lines += MdrDiffLine(UNCHANGED, a[i]); i++; j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    lines += MdrDiffLine(REMOVED, a[i]); i++
                }
                else -> {
                    lines += MdrDiffLine(ADDED, b[j]); j++
                }
            }
        }
        while (i < a.size) lines += MdrDiffLine(REMOVED, a[i++])
        while (j < b.size) lines += MdrDiffLine(ADDED, b[j++])
        return lines
    }

    private const val UNCHANGED = "UNCHANGED"
    private const val REMOVED = "REMOVED"
    private const val ADDED = "ADDED"
}
