package com.malai.card.pan

object Luhn {

    fun isValid(number: String): Boolean {
        if (number.isEmpty() || number.any { !it.isDigit() }) return false
        return checksum(number) % 10 == 0
    }

    fun checkDigit(withoutCheckDigit: String): Int {
        val checksum = checksum(withoutCheckDigit + "0")
        return (10 - checksum % 10) % 10
    }

    private fun checksum(number: String): Int {
        var sum = 0
        val reversed = number.reversed()
        for (index in reversed.indices) {
            var digit = reversed[index] - '0'
            if (index % 2 == 1) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return sum
    }
}
