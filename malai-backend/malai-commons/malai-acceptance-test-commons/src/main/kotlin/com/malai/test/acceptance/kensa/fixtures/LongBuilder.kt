package com.malai.test.acceptance.kensa.fixtures

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.round
import kotlin.random.Random

class LongBuilder {

    private var unique: Boolean = false
    private var range: LongRange = 0..Long.MAX_VALUE
    var excludes: Set<Long> = emptySet()

    /**
     * Returns a Long that is unique within the current JVM instance for the given range
     * or else throws an exception if all unique numbers in the range are exhausted
     */
    fun uniqueInRange(range: LongRange) {
        this.range = range
        uniqueRanges.getOrPut(range) { range.count() to Collections.synchronizedSet(TreeSet()) }
        unique = true
    }

    fun inRange(range: LongRange) {
        this.range = range
    }

    fun lessThan(value: Long) {
        range = 0 until value
    }

    fun greaterThan(value: Long) {
        range = value + 1..Long.MAX_VALUE
    }

    fun build(): Long =
        range.random().let { value ->
            if (unique) {
                uniqueRanges[range]!!.let { (size, previous) ->
                    if (previous.size == size) {
                        throw IllegalStateException("All unique values in range are exhausted")
                    }

                    value.takeUnless { excludes.contains(it) || previous.contains(it) }?.also {
                        previous.add(it)
                    }
                }
            } else value.takeUnless { excludes.contains(it) }
        }
            ?: build()

    companion object {
        private val uniqueRanges: MutableMap<LongRange, Pair<Int, MutableSet<Long>>> = ConcurrentHashMap()
    }
}

class IntBuilder {

    private var unique: Boolean = false
    var range: IntRange = 0..Int.MAX_VALUE
    var excludes: Set<Int> = emptySet()

    /**
     * Returns an Int that is unique within the current JVM instance for the given range
     * or else throws an exception if all unique numbers in the range are exhausted
     */
    fun uniqueInRange(range: IntRange) {
        this.range = range
        uniqueRanges.getOrPut(range) { range.count() to Collections.synchronizedSet(TreeSet()) }
        unique = true
    }

    fun inRange(range: IntRange) {
        this.range = range
    }

    fun lessThan(value: Int) {
        range = 0 until value
    }

    fun greaterThan(value: Int) {
        range = value + 1..Int.MAX_VALUE
    }

    fun build(): Int =
        range.random().let { value ->
            if (unique) {
                uniqueRanges[range]!!.let { (size, previous) ->
                    if (previous.size == size) {
                        throw IllegalStateException("All unique values in range are exhausted")
                    }

                    value.takeUnless { excludes.contains(it) || previous.contains(it) }?.also {
                        previous.add(it)
                    }
                }
            } else value.takeUnless { excludes.contains(it) }
        } ?: build()

    companion object {
        private val uniqueRanges: MutableMap<IntRange, Pair<Int, MutableSet<Int>>> = ConcurrentHashMap()
    }
}

class DoubleBuilder {
    var range: ClosedFloatingPointRange<Double> = 0.0..Double.MAX_VALUE
    var decimalPlaces: Int = 5

    fun inRange(range: ClosedFloatingPointRange<Double>) {
        this.range = range
    }

    fun lessThan(value: Double) {
        range = 0.0..value
    }

    fun greaterThan(value: Double) {
        range = (value + Double.MIN_VALUE)..Double.MAX_VALUE
    }

    fun build(): Double {
        val nextDouble = Random.nextDouble(range.start, range.endInclusive)

        val scale = 10.0.pow(decimalPlaces)

        return round(nextDouble * scale) / scale
    }
}
