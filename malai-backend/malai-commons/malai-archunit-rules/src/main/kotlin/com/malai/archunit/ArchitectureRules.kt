package com.malai.archunit

import com.malai.security.InternalEndpoint
import com.malai.security.NonMutatingEndpoint
import com.malai.security.audit.AuditWriter
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.*
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
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

object ArchitectureRules {

    private val sharedRoots = setOf(
        "com.malai.commons",
        "com.malai.security",
        "com.malai.archunit",
        "com.malai.test",
        "com.malai.client",
    )

    private val mutatingMappings = listOf(
        PostMapping::class.java,
        PutMapping::class.java,
        DeleteMapping::class.java,
        PatchMapping::class.java,
    )

    private val allMappings = mutatingMappings + GetMapping::class.java

    private val declaresMutatingEndpoint =
        object : DescribedPredicate<JavaClass>("declare a @PostMapping/@PutMapping/@PatchMapping/@DeleteMapping method") {
            override fun test(input: JavaClass): Boolean = input.methods.any { method ->
                mutatingMappings.any { method.isAnnotatedWith(it) } &&
                    !method.isAnnotatedWith(NonMutatingEndpoint::class.java)
            }
        }

    private val reachAuditWriterTransitively =
        object : ArchCondition<JavaClass>("reach AuditWriter via the constructor (transitively, depth <= 3)") {
            private val auditWriterClass: Class<*> = AuditWriter::class.java

            override fun check(item: JavaClass, events: ConditionEvents) {
                if (!reaches(item, depth = 3, seen = mutableSetOf())) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} declares a mutating endpoint but no constructor path " +
                                "(direct or via an injected collaborator, depth <= 3) reaches ${auditWriterClass.name}"
                        )
                    )
                }
            }

            private fun reaches(type: JavaClass, depth: Int, seen: MutableSet<String>): Boolean {
                if (depth < 0) return false
                if (!seen.add(type.name)) return false
                return type.constructors.any { ctor ->
                    ctor.rawParameterTypes.any { param ->
                        param.isAssignableTo(auditWriterClass) || reaches(param, depth - 1, seen)
                    }
                }
            }
        }

    fun noCrossModuleInternalAccess(ownRoot: String): ArchRule {
        val excludedRoots = (sharedRoots + ownRoot)
            .map { "$it.." }
            .toTypedArray()
        return noClasses().that().resideInAPackage("$ownRoot..")
            .should().dependOnClassesThat(
                object : DescribedPredicate<JavaClass>(
                    "reside in another com.malai.* domain (not in $ownRoot or a shared root)"
                ) {
                    override fun test(input: JavaClass): Boolean {
                        val pkg = input.packageName
                        if (!pkg.startsWith("com.malai.")) return false
                        return excludedRoots.none { pkg == it.removeSuffix("..") || pkg.startsWith(it.removeSuffix(".")) }
                    }
                }
            )
            .because(
                "BACK-02 / D-2.2: cross-module integration must go through the malai-{domain}-client modules; " +
                    "$ownRoot must not reach into other services' internals"
            )
    }

    /**
     * The inverse of [noCrossModuleInternalAccess]: instead of policing what one module reaches
     * out to, this polices who is allowed to reach *in*. Only [aggregatorRoot] — the deploy unit
     * that imports every service's `@Configuration` — may depend on [serviceRoot]; every other
     * `com.malai.*` module must be blind to it.
     *
     * Written for the AML tipping-off invariant: a customer-path module that could `import
     * com.malai.aml...` is one refactor away from letting an AML case change what the customer
     * sees. There is no aml-client pair by design, so there is no legitimate caller to allow.
     */
    fun onlyTheAggregatorDependsOn(serviceRoot: String, aggregatorRoot: String): ArchRule =
        noClasses().that().resideOutsideOfPackages("$serviceRoot..", "$aggregatorRoot..")
            .should().dependOnClassesThat().resideInAPackage("$serviceRoot..")
            .because(
                "$serviceRoot may only be reached by the $aggregatorRoot deploy aggregator; any other " +
                    "module depending on it could leak its state onto a customer-facing surface"
            )

    val mutatingControllersReachAuditWriter: ArchRule =
        classes()
            .that(declaresMutatingEndpoint)
            .should(reachAuditWriterTransitively)
            .because("BACK-05 / D-2.11: every mutating endpoint must have access to the audit log via its bean graph")

    fun everyControllerMethodHasPreAuthorize(ownControllerRoot: String): ArchRule =
        methods()
            .that(isControllerEndpoint(ownControllerRoot))
            .should().beAnnotatedWith(PreAuthorize::class.java)
            .because(
                "BACK-06: every controller method must declare an explicit access decision via @PreAuthorize"
            )

    fun permitAllOnlyOnAllowlist(
        ownControllerRoot: String,
        allowlistFqns: Set<String>,
    ): ArchRule {
        val violatesAllowlist =
            object : ArchCondition<JavaMethod>("use @PreAuthorize(\"permitAll()\") only when allowlisted") {
                override fun check(method: JavaMethod, events: ConditionEvents) {
                    val expr = method.tryGetAnnotationOfType(PreAuthorize::class.java)
                        .map<String> { it.value }
                        .orElse("")
                    val fqn = "${method.owner.name}.${method.name}"
                    val isPermitAll = expr.replace(" ", "").contains("permitAll()")
                    if (isPermitAll && fqn !in allowlistFqns) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                "$fqn uses @PreAuthorize(\"permitAll()\") but is not on the explicit allowlist"
                            )
                        )
                    }
                }
            }
        return methods()
            .that(isControllerEndpoint(ownControllerRoot))
            .should(violatesAllowlist)
            .because(
                "BACK-06 supplement: opting an endpoint out of authentication must be visible " +
                    "and the allowlist must be edited explicitly"
            )
    }

    fun internalEndpointAnnotationOnInternalPathsOnly(ownControllerRoot: String): ArchRule {
        val methodAtInternalPath =
            object : ArchCondition<JavaMethod>("be declared on a controller whose path matches /api/v<n>/internal/") {
                override fun check(method: JavaMethod, events: ConditionEvents) {
                    val classPaths = method.owner.tryGetAnnotationOfType(RequestMapping::class.java)
                        .map<List<String>> { it.value.toList() + it.path.toList() }
                        .orElse(emptyList())
                    val internalRegex = Regex("/api/v\\d+/internal/.*")
                    if (classPaths.none { it.matches(internalRegex) }) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                "${method.owner.name}.${method.name} is annotated @InternalEndpoint but its " +
                                    "owning controller's @RequestMapping paths $classPaths do not match " +
                                    "/api/v<n>/internal/"
                            )
                        )
                    }
                }
            }
        val normalised = ownControllerRoot.removeSuffix("..")
        return methods()
            .that().areAnnotatedWith(InternalEndpoint::class.java)
            .and().areDeclaredInClassesThat().resideInAPackage("$normalised..")
            .should(methodAtInternalPath)
            .because(
                "@InternalEndpoint marks a service-to-service path; misplacing it on a user-facing controller " +
                    "would be a silent privilege failure"
            )
    }

    val everyScheduledMethodHasSchedulerLock: ArchRule =
        methods()
            .that().areAnnotatedWith(Scheduled::class.java)
            .should().beAnnotatedWith(SchedulerLock::class.java)
            .because(
                "ShedLock coordination prevents N-instance fan-out under ECS scale-out; " +
                    "every @Scheduled method must declare @SchedulerLock"
            )

    fun controllersDoNotCallRepositoriesDirectly(ownRoot: String): ArchRule {
        val controllerPkg = "$ownRoot.controller"
        val repositoryPkg = "$ownRoot.repository"
        val entityPkg = "$ownRoot.entity"
        return noClasses().that().resideInAPackage("$controllerPkg..")
            .should().dependOnClassesThat().resideInAnyPackage("$repositoryPkg..", "$entityPkg..")
            .because(
                "Layering: controllers must call services, which call repositories; " +
                    "skipping the service layer bypasses transactional and audit boundaries"
            )
    }

    fun repositoriesDoNotDependOnServicesOrControllers(ownRoot: String): ArchRule =
        noClasses().that().resideInAPackage("$ownRoot.repository..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "$ownRoot.service..",
                "$ownRoot.controller..",
            )
            .because("Layering: repositories must not depend upwards on services or controllers")

    fun restControllersOnlyInControllerPackage(ownRoot: String): ArchRule =
        classes()
            .that().areAnnotatedWith(RestController::class.java)
            .and().resideInAPackage("$ownRoot..")
            .should().resideInAPackage("$ownRoot.controller..")
            .because("Stereotype location: @RestController belongs in the controller package")

    fun servicesOnlyInServicePackage(ownRoot: String): ArchRule =
        classes()
            .that().areAnnotatedWith(Service::class.java)
            .and().resideInAPackage("$ownRoot..")
            .should().resideInAPackage("$ownRoot.service..")
            .because("Stereotype location: @Service belongs in the service package")

    fun repositoriesOnlyInRepositoryPackage(ownRoot: String): ArchRule =
        classes()
            .that().areAnnotatedWith(Repository::class.java)
            .and().resideInAPackage("$ownRoot..")
            .should().resideInAPackage("$ownRoot.repository..")
            .because("Stereotype location: @Repository belongs in the repository package")

    fun noTransactionalOnControllerClasses(ownRoot: String): ArchRule =
        noClasses().that().resideInAPackage("$ownRoot.controller..")
            .should().beAnnotatedWith(Transactional::class.java)
            .because("Transactional boundary belongs on the service layer, not the controller")

    fun noTransactionalOnControllerMethods(ownRoot: String): ArchRule =
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("$ownRoot.controller..")
            .should().beAnnotatedWith(Transactional::class.java)
            .because("Transactional boundary belongs on the service layer, not the controller")

    fun controllerPathsAreVersioned(ownRoot: String): ArchRule {
        val pathIsVersioned =
            object : ArchCondition<JavaClass>("declare a @RequestMapping path matching /api/v<n>/.*") {
                override fun check(item: JavaClass, events: ConditionEvents) {
                    val paths = item.tryGetAnnotationOfType(RequestMapping::class.java)
                        .map<List<String>> { it.value.toList() + it.path.toList() }
                        .orElse(emptyList())
                    if (paths.isEmpty()) return
                    val versionPattern = Regex("/api/v\\d+/.*")
                    if (paths.none { it.matches(versionPattern) }) {
                        events.add(
                            SimpleConditionEvent.violated(
                                item,
                                "${item.name} declares @RequestMapping paths $paths " +
                                    "that do not match /api/v<n>/"
                            )
                        )
                    }
                }
            }
        return classes().that().areAnnotatedWith(RestController::class.java)
            .and().resideInAPackage("$ownRoot..")
            .should(pathIsVersioned)
            .because("All HTTP-exposed routes must be versioned; un-versioned routes are not safe to evolve")
    }

    fun noFloatingPointInEntities(ownRoot: String): ArchRule {
        val isFloatingPoint =
            object : ArchCondition<JavaField>("not use Float or Double") {
                override fun check(field: JavaField, events: ConditionEvents) {
                    val typeName = field.rawType.name
                    if (typeName == "float" || typeName == "double" ||
                        typeName == "java.lang.Float" || typeName == "java.lang.Double"
                    ) {
                        events.add(
                            SimpleConditionEvent.violated(
                                field,
                                "${field.owner.name}.${field.name} is $typeName; entities must use BigDecimal for amounts"
                            )
                        )
                    }
                }
            }
        return fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areDeclaredInClassesThat().resideInAPackage("$ownRoot..")
            .should(isFloatingPoint)
            .because("Banking entities must use BigDecimal for amounts; Float/Double drop precision")
    }

    fun noLocalDateTimeInEntities(ownRoot: String): ArchRule {
        val isLocalDateTime =
            object : ArchCondition<JavaField>("not be LocalDateTime") {
                override fun check(field: JavaField, events: ConditionEvents) {
                    if (field.rawType.name == LocalDateTime::class.java.name) {
                        events.add(
                            SimpleConditionEvent.violated(
                                field,
                                "${field.owner.name}.${field.name} is LocalDateTime; use Instant for timezone safety"
                            )
                        )
                    }
                }
            }
        return fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areDeclaredInClassesThat().resideInAPackage("$ownRoot..")
            .should(isLocalDateTime)
            .because("Banking entities must use Instant (timezone-safe); LocalDateTime drifts across DST/offset")
    }

    fun enumFieldsOnEntitiesAreStringTyped(ownRoot: String): ArchRule {
        val isEnumeratedString =
            object : ArchCondition<JavaField>("be annotated @Enumerated(STRING)") {
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
        return fields()
            .that().areDeclaredInClassesThat().areAnnotatedWith(Entity::class.java)
            .and().areDeclaredInClassesThat().resideInAPackage("$ownRoot..")
            .should(isEnumeratedString)
            .because("Entity enum fields must round-trip as strings, not ordinals (schema-evolution safety)")
    }

    fun noSystemOut(ownRoot: String): ArchRule {
        val isPrintStreamOrStackTrace =
            object : DescribedPredicate<JavaMethodCall>("calls java.io.PrintStream / Throwable#printStackTrace") {
                override fun test(call: JavaMethodCall): Boolean {
                    val owner = call.targetOwner.name
                    if (owner == "java.io.PrintStream") return true
                    if (owner == "java.lang.Throwable" && call.target.name == "printStackTrace") return true
                    return false
                }
            }
        return noClasses().that().resideInAPackage("$ownRoot..")
            .should().callMethodWhere(isPrintStreamOrStackTrace)
            .because("Use slf4j; System.out/err and printStackTrace leak to stdout outside the log pipeline")
    }

    fun noSpringBeansOnEntitiesOrDtos(ownRoot: String): ArchRule =
        noClasses().that()
            .resideInAnyPackage("$ownRoot.entity..", "$ownRoot.model..")
            .should().beAnnotatedWith(Component::class.java)
            .orShould().beAnnotatedWith(Service::class.java)
            .orShould().beAnnotatedWith(RestController::class.java)
            .orShould().beAnnotatedWith(Repository::class.java)
            .because("Entities and DTOs must not be Spring-managed beans (would imply hidden behaviour)")

    private fun isControllerEndpoint(ownControllerRoot: String): DescribedPredicate<JavaMethod> {
        val pkgPrefix = ownControllerRoot.removeSuffix("..")
        return object : DescribedPredicate<JavaMethod>("controller endpoint in $ownControllerRoot") {
            override fun test(method: JavaMethod): Boolean {
                val hasMapping = allMappings.any { method.isAnnotatedWith(it) }
                if (!hasMapping) return false
                val ownerPkg = method.owner.packageName
                return ownerPkg == pkgPrefix || ownerPkg.startsWith("$pkgPrefix.")
            }
        }
    }

    private const val HTTP_BUILDER_COMPANION =
        "com.malai.test.acceptance.kensa.stub.HttpRequest\$HttpRequestBuilder\$Companion"

    private val MUTATING_BUILDER_METHODS = setOf("post", "put", "delete", "patch")

    val DEFAULT_AUDIT_ASSERTION_HELPERS: Set<String> = setOf(
        "theAuditEventsInTheDatabase",
        "theLastAuditEventWithAction",
        "thereIsAnAuditEventWithAction",
        "theNumberOfAuditEventsWithAction",
        "theAuditEventRowsInTheDatabase",
    )

    val DEFAULT_IDEMPOTENCY_HELPERS: Set<String> = setOf(
        "theIdempotencyKeyResponseBody",
        "thereIsNoIdempotencyKeyInTheDatabase",
        "theIdempotencyKeyInTheDatabase",
        "theIdempotencyKeysInTheDatabase",
    )

    private val callsMutatingHttpBuilder =
        object : DescribedPredicate<JavaClass>("call a mutating HTTP request builder (post/put/delete/patch)") {
            override fun test(input: JavaClass): Boolean =
                input.methodCallsFromSelf.any { call ->
                    call.targetOwner.name == HTTP_BUILDER_COMPANION &&
                        call.target.name in MUTATING_BUILDER_METHODS
                }
        }

    private fun callsAnyHelper(helperNames: Set<String>): ArchCondition<JavaClass> =
        object : ArchCondition<JavaClass>("call one of ${helperNames.sorted()}") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val found = item.methodCallsFromSelf.any { call -> call.target.name in helperNames }
                if (!found) {
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            "${item.name} sends a mutating HTTP request but calls none of: " +
                                helperNames.sorted().joinToString(", "),
                        )
                    )
                }
            }
        }

    /**
     * BACK-05 / D-2.11 test-coverage half: every controller test in `..acceptance.happy..` that
     * sends a POST/PUT/DELETE/PATCH must call one of the audit assertion helpers
     * (`theAuditEventsInTheDatabase`, `theLastAuditEventWithAction`, ...). Catches "the controller
     * emits an audit event but the test never asserts on it" — the structural other half of the
     * production-side `mutatingControllersReachAuditWriter` rule.
     */
    fun controllerTestsAssertOnAudit(
        happyPackage: String,
        helperNames: Set<String> = DEFAULT_AUDIT_ASSERTION_HELPERS,
    ): ArchRule {
        val normalised = happyPackage.removeSuffix("..")
        return classes()
            .that().haveSimpleNameEndingWith("ControllerTest")
            .and().resideInAPackage("$normalised..")
            .and(callsMutatingHttpBuilder)
            .should(callsAnyHelper(helperNames))
            .because(
                "Every controller test that exercises a mutating endpoint must assert on the audit chain via the " +
                    "shared AuditTestSupport helpers; otherwise the audit emission is structurally present but " +
                    "behaviourally untested"
            )
    }

    /**
     * Every `*ControllerTest` in `..acceptance.happy..` must have a counterpart in `..acceptance.sad..`.
     * Class-name-based existence check. Use `@ArchTest fun` form because the condition needs the full
     * imported-class set (siblings live in a different package).
     */
    fun assertSadCounterpartsExist(
        importedClasses: JavaClasses,
        happyPackage: String,
        sadPackage: String,
    ) {
        val normalisedHappy = happyPackage.removeSuffix("..").removeSuffix(".")
        val normalisedSad = sadPackage.removeSuffix("..").removeSuffix(".")
        val sadNames: Set<String> = importedClasses
            .filter { it.packageName == normalisedSad }
            .map { it.simpleName }
            .toSet()
        val missing: List<String> = importedClasses
            .filter { it.packageName == normalisedHappy && it.simpleName.endsWith("ControllerTest") }
            .map { it.simpleName }
            .filter { it !in sadNames }
        if (missing.isNotEmpty()) {
            throw AssertionError(
                "Every *ControllerTest in $normalisedHappy must have a counterpart in $normalisedSad. " +
                    "Missing sad-path tests for: ${missing.sorted().joinToString(", ")}"
            )
        }
    }

    /**
     * For each `*ControllerTest` (happy or sad) whose simple name is in [protectedControllerTests],
     * verify the class calls at least one idempotency helper. The list is per-service because the
     * set of idempotency-protected paths is declared per service (see [com.malai.security.idempotency.IdempotencyProtectedRoutes]).
     */
    fun assertIdempotencyAwareTestsAssertOnIdempotency(
        importedClasses: JavaClasses,
        acceptancePackage: String,
        protectedControllerTests: Set<String>,
        helperNames: Set<String> = DEFAULT_IDEMPOTENCY_HELPERS,
    ) {
        if (protectedControllerTests.isEmpty()) return
        val normalised = acceptancePackage.removeSuffix("..").removeSuffix(".")
        val matched = importedClasses
            .filter { c ->
                c.packageName.startsWith(normalised) &&
                    c.simpleName in protectedControllerTests
            }
        val foundNames = matched.map { it.simpleName }.toSet()
        val unresolved = protectedControllerTests - foundNames
        if (unresolved.isNotEmpty()) {
            throw AssertionError(
                "Idempotency rule references test classes that do not exist under $normalised: " +
                    "${unresolved.sorted().joinToString(", ")}. Update IDEMPOTENCY_PROTECTED_CONTROLLER_TESTS."
            )
        }
        val missing: List<String> = matched
            .filterNot { c -> c.methodCallsFromSelf.any { call -> call.target.name in helperNames } }
            .map { it.name }
        if (missing.isNotEmpty()) {
            throw AssertionError(
                "Tests for idempotency-protected controllers must assert on idempotency via one of: " +
                    "${helperNames.sorted().joinToString(", ")}. " +
                    "The following test classes do not: ${missing.sorted().joinToString(", ")}"
            )
        }
    }
}
