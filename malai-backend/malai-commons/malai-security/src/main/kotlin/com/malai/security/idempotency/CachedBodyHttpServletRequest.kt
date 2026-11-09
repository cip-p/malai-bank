package com.malai.security.idempotency

import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.IDEMPOTENCY_PAYLOAD_TOO_LARGE
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

class CachedBodyHttpServletRequest(
    request: HttpServletRequest,
    maxBodyBytes: Int,
) : HttpServletRequestWrapper(request) {

    val cachedBody: ByteArray = drainCapped(request, maxBodyBytes)

    override fun getInputStream(): ServletInputStream = CachedBodyServletInputStream(cachedBody)

    override fun getReader(): BufferedReader =
        BufferedReader(InputStreamReader(getInputStream(), characterEncoding ?: Charsets.UTF_8.name()))

    private class CachedBodyServletInputStream(body: ByteArray) : ServletInputStream() {
        private val source = ByteArrayInputStream(body)
        override fun isFinished(): Boolean = source.available() == 0
        override fun isReady(): Boolean = true
        override fun setReadListener(listener: ReadListener?) {}
        override fun read(): Int = source.read()
    }

    private companion object {
        fun drainCapped(request: HttpServletRequest, maxBodyBytes: Int): ByteArray {
            val declared = request.contentLengthLong
            if (declared > maxBodyBytes) {
                throw BadRequestException(IDEMPOTENCY_PAYLOAD_TOO_LARGE)
            }
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            request.inputStream.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    if (out.size() + read > maxBodyBytes) {
                        throw BadRequestException(IDEMPOTENCY_PAYLOAD_TOO_LARGE)
                    }
                    out.write(buffer, 0, read)
                }
            }
            return out.toByteArray()
        }
    }
}
