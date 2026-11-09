package com.malai.standingorder.architecture

import com.malai.archunit.ArchitectureRules
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule

private const val ACCEPTANCE_PKG = "com.malai.standingorder.acceptance"
private const val HAPPY_PKG = "$ACCEPTANCE_PKG.happy"
private const val SAD_PKG = "$ACCEPTANCE_PKG.sad"

private val IDEMPOTENCY_PROTECTED_CONTROLLER_TESTS = emptySet<String>()

@AnalyzeClasses(packages = [ACCEPTANCE_PKG])
class StandingOrderServiceTestArchitectureTest {

    @ArchTest
    val controllerTestsAssertOnAudit: ArchRule =
        ArchitectureRules.controllerTestsAssertOnAudit(HAPPY_PKG).allowEmptyShould(true)

    @ArchTest
    fun everyHappyControllerTestHasASadCounterpart(importedClasses: JavaClasses) {
        ArchitectureRules.assertSadCounterpartsExist(importedClasses, HAPPY_PKG, SAD_PKG)
    }

    @ArchTest
    fun idempotencyAwareTestsAssertOnIdempotency(importedClasses: JavaClasses) {
        ArchitectureRules.assertIdempotencyAwareTestsAssertOnIdempotency(
            importedClasses,
            ACCEPTANCE_PKG,
            IDEMPOTENCY_PROTECTED_CONTROLLER_TESTS,
        )
    }
}
