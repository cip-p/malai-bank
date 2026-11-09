package com.malai.security.architecture

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaField
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.scheduling.annotation.Scheduled
import java.time.LocalDateTime

private const val OWN_ROOT = "com.malai.security"

@AnalyzeClasses(packages = [OWN_ROOT], importOptions = [ImportOption.DoNotIncludeTests::class])
class MalaiSecurityArchitectureTest {

    @ArchTest
    val auditWriterFullyQualifiedNameIsPinned: ArchRule =
        classes()
            .that().haveFullyQualifiedName("com.malai.security.audit.AuditWriter")
            .should().beInterfaces()
            .because(
                "mutatingControllersReachAuditWriter and every mutating service bind to this exact name; " +
                    "chain-core refactors must not move or rename it"
            )

    @ArchTest
    val auditReadersNeitherLockNorWrite: ArchRule = run {
        val writesThroughJdbc =
            object : DescribedPredicate<JavaMethodCall>("writes through JdbcTemplate") {
                override fun test(call: JavaMethodCall): Boolean =
                    call.targetOwner.name == "org.springframework.jdbc.core.JdbcTemplate" &&
                        call.target.name in setOf("update", "batchUpdate", "execute")
            }
        noClasses().that().resideInAPackage("$OWN_ROOT.audit..")
            .and().haveSimpleNameEndingWith("Reader")
            .should().callMethodWhere(writesThroughJdbc)
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.malai.security.chain.ChainHeads")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("com.malai.security.chain.ChainAppender")
            .because(
                "a reader that took the chain_head FOR UPDATE lock (the only path to it is ChainHeads) " +
                    "would serialise every read behind every in-flight append, and a reader that wrote " +
                    "would break the append-only contract AuditChainVerifier depends on"
            )
    }

    @ArchTest
    val everyScheduledMethodHasSchedulerLock: ArchRule =
        methods()
            .that().areAnnotatedWith(Scheduled::class.java)
            .should().beAnnotatedWith(SchedulerLock::class.java)
            .because(
                "ShedLock coordination prevents N-instance fan-out under ECS scale-out; " +
                    "every @Scheduled method must declare @SchedulerLock"
            )

    @ArchTest
    val noFloatingPointInEntities: ArchRule =
        noFields().that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areDeclaredInClassesThat().resideInAPackage("$OWN_ROOT..")
            .should().haveRawType(java.lang.Float.TYPE)
            .orShould().haveRawType(Float::class.java)
            .orShould().haveRawType(java.lang.Double.TYPE)
            .orShould().haveRawType(Double::class.java)
            .allowEmptyShould(true)
            .because("Banking entities must use BigDecimal for amounts; Float/Double drop precision")

    @ArchTest
    val noLocalDateTimeInEntities: ArchRule =
        noFields().that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areDeclaredInClassesThat().resideInAPackage("$OWN_ROOT..")
            .should().haveRawType(LocalDateTime::class.java)
            .allowEmptyShould(true)
            .because("Banking entities must use Instant (timezone-safe); LocalDateTime drifts across DST/offset")

    @ArchTest
    val enumFieldsOnEntitiesAreStringTyped: ArchRule = run {
        val condition = object : ArchCondition<JavaField>("be annotated @Enumerated(STRING)") {
            override fun check(item: JavaField, events: ConditionEvents) {
                if (!item.rawType.isEnum) return
                val enumerated = item.tryGetAnnotationOfType(Enumerated::class.java)
                if (!enumerated.isPresent || enumerated.get().value != EnumType.STRING) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.owner.name}.${item.name} is an enum field but is not @Enumerated(STRING)"
                        )
                    )
                }
            }
        }
        fields().that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areDeclaredInClassesThat().resideInAPackage("$OWN_ROOT..")
            .should(condition)
            .allowEmptyShould(true)
            .because("Entity enum fields must round-trip as strings, not ordinals")
    }

    @ArchTest
    val noSystemOut: ArchRule = run {
        val callsPrintStream =
            object : DescribedPredicate<JavaMethodCall>("calls java.io.PrintStream / Throwable#printStackTrace") {
                override fun test(call: JavaMethodCall): Boolean {
                    val owner = call.targetOwner.name
                    if (owner == "java.io.PrintStream") return true
                    if (owner == "java.lang.Throwable" && call.target.name == "printStackTrace") return true
                    return false
                }
            }
        noClasses().that().resideInAPackage("$OWN_ROOT..")
            .should().callMethodWhere(callsPrintStream)
            .because("Use slf4j; System.out/err and printStackTrace leak to stdout outside the log pipeline")
    }
}
