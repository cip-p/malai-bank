package com.malai.test.acceptance.kensa.fixtures

import kotlin.random.Random

enum class CharacterClass {
    Alphabetic,
    Alphanumeric,
    Numeric
}

class StringBuilder {
    var length = 10
    var upperCase = true
    var characterClass = CharacterClass.Alphabetic

    fun build(): String = CharacterBuilder(upperCase, characterClass).run {
        CharArray(length) { build() }
    }.let(::String)
}

class CharacterBuilder(var uppercase: Boolean = true, var characterClass: CharacterClass = CharacterClass.Alphabetic) {

    fun build(): Char =
        when (characterClass) {
            CharacterClass.Alphabetic -> applyCase(oneOf(letters))
            CharacterClass.Alphanumeric -> applyCase(oneOf(digitsAndLetters))
            CharacterClass.Numeric -> oneOf(digits)
        }

    private fun oneOf(chars: List<Char>) = chars[Random.nextInt(chars.size)]

    private fun applyCase(char: Char) = if (uppercase) char.uppercaseChar() else char

    companion object {
        private val digits = ('0'..'9').toList()
        private val letters = ('a'..'z').toList()
        private val digitsAndLetters = digits + letters
    }
}
