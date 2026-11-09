package com.malai.accountholder.model

data class ProfileImage(
    val contentType: String,
    val imageData: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProfileImage) return false
        return contentType == other.contentType && imageData.contentEquals(other.imageData)
    }

    override fun hashCode(): Int = 31 * contentType.hashCode() + imageData.contentHashCode()
}
