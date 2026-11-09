package com.malai.card.pan

import org.springframework.stereotype.Component
import java.security.SecureRandom

@Component
class RandomPanGenerator : PanGenerator {

    private val random = SecureRandom()

    override fun generatePan(): String {
        val withoutCheckDigit = buildString {
            append(Pan.MALAI_BIN)
            repeat(Pan.PAN_LENGTH - Pan.MALAI_BIN.length - 1) { append(random.nextInt(10)) }
        }
        return withoutCheckDigit + Luhn.checkDigit(withoutCheckDigit)
    }

    override fun generateCvv(): String = "%03d".format(random.nextInt(1000))
}
