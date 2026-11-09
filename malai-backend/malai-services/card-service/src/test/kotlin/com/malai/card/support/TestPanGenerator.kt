package com.malai.card.support

import com.malai.card.pan.PanGenerator

class TestPanGenerator : PanGenerator {

    private val pans: ArrayDeque<String> = ArrayDeque()
    private val cvvs: ArrayDeque<String> = ArrayDeque()

    fun willReturnPan(vararg pans: String) {
        this.pans.addAll(pans)
    }

    fun willReturnCvv(vararg cvvs: String) {
        this.cvvs.addAll(cvvs)
    }

    override fun generatePan(): String = pans.removeFirstOrNull() ?: DEFAULT_PAN

    override fun generateCvv(): String = cvvs.removeFirstOrNull() ?: DEFAULT_CVV

    fun reset() {
        pans.clear()
        cvvs.clear()
    }

    companion object {
        const val DEFAULT_PAN = "9904320000000000"
        const val DEFAULT_CVV = "000"
    }
}
