package com.malai.commons.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest(classes = [ApplicationVersionApplication::class])
@TestPropertySource(properties = ["spring.application.name=TestingApp"])
class ApplicationVersionTest {

    @Autowired
    lateinit var applicationVersionHolder: ApplicationVersionHolder

    @Test
    fun `should hold application version`() {
        val version = applicationVersionHolder.version
        println(version)
        assertNotNull(version)
    }

    @Test
    fun `should prefer MAL_APP_VERSION env var over manifest`() {
        val holder = ApplicationVersionHolder { name -> if (name == "MAL_APP_VERSION") "1777195155" else null }
        assertEquals("1777195155", holder.version)
    }

    @Test
    fun `should fall back to manifest when MAL_APP_VERSION env var is blank`() {
        val fromManifestOnly = ApplicationVersionHolder { null }.version
        val withBlankEnvVar = ApplicationVersionHolder { name -> if (name == "MAL_APP_VERSION") "" else null }
        assertEquals(fromManifestOnly, withBlankEnvVar.version)
    }
}

// Phase 2 BACK-07: see HttpExceptionHandlerTest comment — same DataSource/JPA
// autoconfig exclusion needed now that malai-commons declares
// spring-boot-starter-data-jpa (provided).
@SpringBootApplication(excludeName = [
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
])
class ApplicationVersionApplication
