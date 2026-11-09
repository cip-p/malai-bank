package com.malai.client.malai.stub.card.builders.response

class CardMerchantResponseBuilder private constructor() {

    private var merchantId: String
    private var name: String
    private var mcc: String

    init {
        merchantId = "fictional-coffee"
        name = "Fictional Coffee Co"
        mcc = "5814"
    }

    fun withMerchantId(merchantId: String) = apply { this.merchantId = merchantId }
    fun withName(name: String) = apply { this.name = name }
    fun withMcc(mcc: String) = apply { this.mcc = mcc }

    fun toMap(): Map<String, Any?> = linkedMapOf(
        "merchantId" to merchantId,
        "name" to name,
        "mcc" to mcc,
    )

    companion object {
        fun aCardMerchantResponse() = CardMerchantResponseBuilder()
    }
}
