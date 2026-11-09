package com.malai.api.architecture

import com.malai.archunit.ArchitectureRules
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaMethod
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.web.bind.annotation.*

/**
 * `ImportOption.DoNotIncludeTests` only filters out `target/test-classes/`. The malai-api test
 * classpath also pulls in `*-tests.jar` artifacts from every bundled service (test-jar dep), so
 * we additionally exclude those — otherwise test-only controllers (`TestSecurityController`,
 * `TestCorsController`) and test classes pollute the production-architecture scan.
 */
class DoNotIncludeTestJars : ImportOption {
    override fun includes(location: Location): Boolean = !location.contains("-tests.jar")
}

@AnalyzeClasses(
    packages = ["com.malai"],
    importOptions = [ImportOption.DoNotIncludeTests::class, DoNotIncludeTestJars::class],
)
class MalaiApiArchitectureTest {

    @ArchTest
    val everyScheduledMethodHasSchedulerLock: ArchRule =
        ArchitectureRules.everyScheduledMethodHasSchedulerLock

    @ArchTest
    val methodSecurityIsEnabled: ArchRule =
        classes()
            .that().areAnnotatedWith(EnableMethodSecurity::class.java)
            .should().resideInAPackage("com.malai..")
            .allowEmptyShould(true)
            .because(
                "Spring's @PreAuthorize is only enforced when @EnableMethodSecurity is present on a @Configuration; " +
                    "without it every controller's role gate is decorative."
            )

    @ArchTest
    val controllerEndpointsBelongToVersionedSpace: ArchRule =
        methods()
            .that(isAnyControllerEndpoint)
            .should(
                haveEffectivePathMatching(
                    Regex("^/(api/v\\d+|ready|version|openapi\\.yaml|(fps|swift|bacs|cards)/callbacks|internal/admin)(/.*)?$"),
                ),
            )
            .because(
                "Every HTTP-exposed route in malai-api lives under /api/v<n>/ except three reserved " +
                    "operational endpoints: /ready, /version, and /openapi.yaml (the OpenAPI cross-repo " +
                    "contract — BACK-09, Plan 02-16), plus the per-rail gateway scheme-callback surface " +
                    "(/<rail>/callbacks/**) the external payment schemes call back on, plus " +
                    "/internal/admin/** for e2e-tests-profile plumbing endpoints (e.g. clearing " +
                    "ServiceTokenClient caches between tests) — these beans are only registered when " +
                    "the e2e-tests profile is active so they're absent in dev / prod deployments"
            )

    @ArchTest
    val noTwoEndpointsShareTheSameVerbAndPath: ArchRule =
        methods()
            .that(isAnyControllerEndpoint)
            .should(noOtherEndpointSharesVerbAndPath())
            .because(
                "Two controller methods with the same HTTP verb and effective path resolve in bean-load order — " +
                    "silent route hijack risk"
            )

    @ArchTest
    val noCustomerPathModuleDependsOnAmlService: ArchRule =
        ArchitectureRules.onlyTheAggregatorDependsOn("com.malai.aml", "com.malai.api")

    @ArchTest
    val commonsDoesNotDependOnDomain: ArchRule =
        noClasses().that().resideInAPackage("com.malai.commons..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.malai.auth..",
                "com.malai.accounts..",
                "com.malai.accountholder..",
                "com.malai.payee..",
                "com.malai.directdebit..",
                "com.malai.standingorder..",
                "com.malai.api..",
            )
            .because("malai-commons must remain reusable; depending on a domain inverts the module graph")

    @ArchTest
    val securityDoesNotDependOnDomain: ArchRule =
        noClasses().that().resideInAPackage("com.malai.security..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.malai.auth..",
                "com.malai.accounts..",
                "com.malai.accountholder..",
                "com.malai.payee..",
                "com.malai.directdebit..",
                "com.malai.standingorder..",
                "com.malai.api..",
            )
            .because("malai-security sits below every domain in the dep graph")

    @ArchTest
    val clientsDoNotDependOnDomain: ArchRule =
        noClasses().that().resideInAPackage("com.malai.client..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.malai.auth..",
                "com.malai.accounts..",
                "com.malai.accountholder..",
                "com.malai.payee..",
                "com.malai.directdebit..",
                "com.malai.standingorder..",
                "com.malai.api..",
            )
            .because(
                "A client module must not reach into the service's internals — otherwise the cross-module " +
                    "isolation rule is enforced from one side only"
            )
}

private val mappingAnnotationClasses = arrayOf(
    GetMapping::class.java,
    PostMapping::class.java,
    PutMapping::class.java,
    PatchMapping::class.java,
    DeleteMapping::class.java,
)

private val isAnyControllerEndpoint =
    object : DescribedPredicate<JavaMethod>("controller endpoint method") {
        override fun test(method: JavaMethod): Boolean =
            mappingAnnotationClasses.any { method.isAnnotatedWith(it) }
    }

private fun effectivePaths(method: JavaMethod): List<String> {
    val classPaths = method.owner.tryGetAnnotationOfType(RequestMapping::class.java)
        .map<List<String>> { it.value.toList() + it.path.toList() }
        .orElse(emptyList())
        .ifEmpty { listOf("") }
        .map { it.distinct() }
    val ann = mappingAnnotationClasses.firstOrNull { method.isAnnotatedWith(it) } ?: return emptyList()
    val a = method.tryGetAnnotationOfType(ann)
    if (!a.isPresent) return emptyList()
    val value = (ann.getMethod("value").invoke(a.get()) as Array<*>).filterIsInstance<String>()
    val path = (ann.getMethod("path").invoke(a.get()) as Array<*>).filterIsInstance<String>()
    val methodPaths = (value + path).distinct().ifEmpty { listOf("") }
    return classPaths.flatMap { c ->
        methodPaths.map { m ->
            val joined = c.trimEnd('/') + "/" + m.trimStart('/')
            joined.ifEmpty { "/" }.removeSuffix("/").ifEmpty { "/" }
        }
    }.distinct()
}

private fun List<String>.distinct(): List<String> = toSet().toList()

private fun String.distinct(): String = this

private fun verbOf(method: JavaMethod): String =
    mappingAnnotationClasses.firstOrNull { method.isAnnotatedWith(it) }?.simpleName ?: "UNKNOWN"

private fun haveEffectivePathMatching(regex: Regex): ArchCondition<JavaMethod> =
    object : ArchCondition<JavaMethod>("be exposed at a path matching ${regex.pattern}") {
        override fun check(method: JavaMethod, events: ConditionEvents) {
            val paths = effectivePaths(method)
            if (paths.none { it.matches(regex) }) {
                events.add(
                    SimpleConditionEvent.violated(
                        method,
                        "${method.owner.name}.${method.name} resolves to $paths, none match ${regex.pattern}"
                    )
                )
            }
        }
    }

private fun noOtherEndpointSharesVerbAndPath(): ArchCondition<JavaMethod> =
    object : ArchCondition<JavaMethod>("not share verb+path with another endpoint") {
        private val seen = mutableMapOf<String, String>()

        override fun check(method: JavaMethod, events: ConditionEvents) {
            val verb = verbOf(method)
            val fqn = "${method.owner.name}.${method.name}"
            effectivePaths(method).forEach { path ->
                val key = "$verb $path"
                val previous = seen[key]
                if (previous != null && previous != fqn) {
                    events.add(
                        SimpleConditionEvent.violated(
                            method,
                            "$fqn shares route '$key' with $previous"
                        )
                    )
                } else {
                    seen[key] = fqn
                }
            }
        }
    }
