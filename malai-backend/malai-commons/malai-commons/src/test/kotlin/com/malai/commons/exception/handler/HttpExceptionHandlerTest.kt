package com.malai.commons.exception.handler

import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.ForbiddenException
import com.malai.commons.exception.InternalServerErrorException
import com.malai.commons.exception.MalaiCode.*
import com.malai.commons.exception.NotFoundException
import com.malai.commons.exception.UnauthorizedException
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.MethodParameter
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.resource.NoResourceFoundException

@SpringBootTest(classes = [HttpExceptionHandlerApplication::class])
@AutoConfigureMockMvc
class HttpExceptionHandlerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: ExceptionHandlerService

    @Test
    fun `handle Exception`() {
        given(service.doSomething()).willAnswer {
            throw Exception("test exception")
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle NotFoundException`() {
        given(service.doSomething()).willAnswer {
            throw NotFoundException(GENERIC_NOT_FOUND, "test exception")
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("8999"))
    }

    @Test
    fun `handle InternalServerErrorException`() {
        given(service.doSomething()).willAnswer {
            throw InternalServerErrorException(GENERIC_ERROR)
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle ConflictErrorException`() {
        given(service.doSomething()).willAnswer {
            throw ConflictErrorException(GENERIC_CONFLICT)
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("6999"))
    }

    @Test
    fun `handle UnauthorizedException`() {
        given(service.doSomething()).willAnswer {
            throw UnauthorizedException(GENERIC_UNAUTHORIZED, "test exception")
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("5999"))
    }

    @Test
    fun `handle BadRequestException`() {
        given(service.doSomething()).willAnswer {
            throw BadRequestException(GENERIC_BAD_REQUEST)
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("7999"))
    }

    @Test
    fun `handle ForbiddenException`() {
        given(service.doSomething()).willAnswer {
            throw ForbiddenException(GENERIC_FORBIDDEN, "test exception")
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("4999"))
    }

    @Test
    fun `handle ServletRequestBindingException`() {
        given(service.doSomething()).willAnswer {
            throw ServletRequestBindingException("test exception")
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle MissingServletRequestPartException`() {
        given(service.doSomething()).willAnswer {
            throw MissingServletRequestPartException("test exception")
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle MethodArgumentTypeMismatchException`() {
        val methodParameter = mock(MethodParameter::class.java)
        given(service.doSomething()).willAnswer {
            throw MethodArgumentTypeMismatchException(
                "Object",
                String::class.java,
                "argumentName",
                methodParameter,
                Throwable()
            )
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle HttpMessageNotReadableException`() {
        mockMvc.perform(
            post("/test-exception-handler-with-body")
                .contentType(APPLICATION_JSON)
                .content("""{"field1": "value1"}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle HttpRequestMethodNotSupportedException`() {
        mockMvc.perform(post("/test-exception-handler"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle NoHandlerFoundException`() {
        mockMvc.perform(get("/unknown-path"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("9999"))
    }

    @Test
    fun `handle NoResourceFoundException`() {
        given(service.doSomething()).willAnswer {
            throw NoResourceFoundException(HttpMethod.GET, "/some-path", "/some-path")
        }

        mockMvc.perform(get("/test-exception-handler"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("9999"))
    }
}

// Phase 2 BACK-07: malai-commons gained `spring-boot-starter-data-jpa` (provided)
// for IdempotencyKeyEntity. The starter is on the test classpath via the
// transitive `spring-boot-starter-test` dep, which triggers Spring Boot
// JPA + DataSource autoconfiguration; this acceptance-style test has no
// real DB so we exclude both autoconfigurations explicitly.
@SpringBootApplication(excludeName = [
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
])
class HttpExceptionHandlerApplication

