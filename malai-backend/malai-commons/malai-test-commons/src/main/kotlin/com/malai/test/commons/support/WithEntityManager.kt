package com.malai.test.commons.support

import com.malai.test.commons.support.WithEntityManager.TestEntityManagerExtension
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.beans.factory.getBean
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(TestEntityManagerExtension::class)
interface WithEntityManager {

    fun entityManager(): EntityManager {
        return MalaiApiTestContext.context(TestEntityManagerContext::class.java).entityManager!!
    }

    fun persist(entity: Any) = entityManager().persist(entity)

    fun flush() = entityManager().flush()

    fun persistAndFlush(entity: Any) {
        persist(entity)
        flush()
    }

    class TestEntityManagerExtension : BeforeAllCallback {
        private var entityManager: EntityManager? = null

        override fun beforeAll(extensionContext: ExtensionContext) {
            if (entityManager == null) {
                entityManager = SpringExtension.getApplicationContext(extensionContext).getBean<EntityManager>()
            }
            MalaiApiTestContext.context(TestEntityManagerContext::class.java).entityManager = entityManager
        }
    }

    class TestEntityManagerContext {
        var entityManager: EntityManager? = null
    }
}
