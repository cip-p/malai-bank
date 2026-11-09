package com.malai.commons.tracking

import com.malai.commons.tracking.MdcTraceIdServletFilterConfig.Companion.MDC_TRACE_ID_HEADER
import com.malai.commons.tracking.MdcTraceIdServletFilterConfig.Companion.MDC_TRACE_ID_KEY
import jakarta.servlet.Filter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class MdcTraceIdServletFilterConfigTest {

    private val filter: Filter = requireNotNull(MdcTraceIdServletFilterConfig().e2eMdcTraceIdServletFilter().filter)

    @AfterEach
    fun clearMdc() {
        MDC.remove(MDC_TRACE_ID_KEY)
    }

    @Test
    fun `a request arriving without a trace id is given one for the duration of the request`() {
        val seen = traceIdSeenDuring(MockHttpServletRequest())

        assertThat(seen).isNotNull()
    }

    @Test
    fun `the generated trace id is shaped like the one the otel agent would have put there`() {
        val seen = traceIdSeenDuring(MockHttpServletRequest())

        assertThat(seen).matches("[0-9a-f]{32}")
    }

    @Test
    fun `an inbound trace id header is honoured so a caller can correlate what it drove`() {
        val request = MockHttpServletRequest().apply {
            addHeader(MDC_TRACE_ID_HEADER, "0af7651916cd43dd8448eb211c80319c")
        }

        assertThat(traceIdSeenDuring(request)).isEqualTo("0af7651916cd43dd8448eb211c80319c")
    }

    @Test
    fun `a trace id already on the mdc is never overwritten because the agent owns it`() {
        MDC.put(MDC_TRACE_ID_KEY, "4bf92f3577b34da6a3ce929d0e0e4736")

        assertThat(traceIdSeenDuring(MockHttpServletRequest()))
            .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736")
    }

    @Test
    fun `a trace id already on the mdc survives the request rather than being cleared away`() {
        MDC.put(MDC_TRACE_ID_KEY, "4bf92f3577b34da6a3ce929d0e0e4736")

        traceIdSeenDuring(MockHttpServletRequest())

        assertThat(MDC.get(MDC_TRACE_ID_KEY)).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736")
    }

    @Test
    fun `a generated trace id is cleared once the request is done so a pooled thread cannot leak it`() {
        traceIdSeenDuring(MockHttpServletRequest())

        assertThat(MDC.get(MDC_TRACE_ID_KEY)).isNull()
    }

    @Test
    fun `a generated trace id is cleared even when the request blows up mid-chain`() {
        val chain = MockFilterChain(object : jakarta.servlet.http.HttpServlet() {
            override fun service(
                req: jakarta.servlet.http.HttpServletRequest,
                res: jakarta.servlet.http.HttpServletResponse,
            ) = throw IllegalStateException("the request failed")
        })

        runCatching { filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse(), chain) }

        assertThat(MDC.get(MDC_TRACE_ID_KEY)).isNull()
    }

    @Test
    fun `two requests are given different trace ids`() {
        val first = traceIdSeenDuring(MockHttpServletRequest())
        val second = traceIdSeenDuring(MockHttpServletRequest())

        assertThat(first).isNotEqualTo(second)
    }

    private fun traceIdSeenDuring(request: MockHttpServletRequest): String? {
        var seen: String? = null
        val chain = MockFilterChain(object : jakarta.servlet.http.HttpServlet() {
            override fun service(
                req: jakarta.servlet.http.HttpServletRequest,
                res: jakarta.servlet.http.HttpServletResponse,
            ) {
                seen = MDC.get(MDC_TRACE_ID_KEY)
            }
        })
        filter.doFilter(request, MockHttpServletResponse(), chain)
        return seen
    }
}
