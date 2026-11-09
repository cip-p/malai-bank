package com.malai.payments.architecture

import com.malai.archunit.ArchitectureRules
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.freeze.FreezingArchRule

private const val OWN_ROOT = "com.malai.payments"
private const val OWN_CONTROLLER_ROOT = "$OWN_ROOT.controller.."

@AnalyzeClasses(packages = [OWN_ROOT], importOptions = [ImportOption.DoNotIncludeTests::class])
class PaymentsServiceArchitectureTest {

    @ArchTest
    val noCrossModuleInternalAccess: ArchRule =
        FreezingArchRule.freeze(ArchitectureRules.noCrossModuleInternalAccess(OWN_ROOT))

    @ArchTest
    val mutatingControllersReachAuditWriter: ArchRule =
        FreezingArchRule.freeze(ArchitectureRules.mutatingControllersReachAuditWriter)

    @ArchTest
    val everyControllerMethodHasPreAuthorize: ArchRule =
        ArchitectureRules.everyControllerMethodHasPreAuthorize(OWN_CONTROLLER_ROOT)

    @ArchTest
    val permitAllOnlyOnAllowlist: ArchRule =
        ArchitectureRules.permitAllOnlyOnAllowlist(OWN_CONTROLLER_ROOT, emptySet())

    @ArchTest
    val internalEndpointAnnotationOnInternalPathsOnly: ArchRule =
        ArchitectureRules.internalEndpointAnnotationOnInternalPathsOnly(OWN_CONTROLLER_ROOT).allowEmptyShould(true)

    @ArchTest
    val everyScheduledMethodHasSchedulerLock: ArchRule =
        ArchitectureRules.everyScheduledMethodHasSchedulerLock.allowEmptyShould(true)

    @ArchTest
    val controllersDoNotCallRepositoriesDirectly: ArchRule =
        ArchitectureRules.controllersDoNotCallRepositoriesDirectly(OWN_ROOT)

    @ArchTest
    val repositoriesDoNotDependOnServicesOrControllers: ArchRule =
        ArchitectureRules.repositoriesDoNotDependOnServicesOrControllers(OWN_ROOT).allowEmptyShould(true)

    @ArchTest
    val restControllersOnlyInControllerPackage: ArchRule =
        ArchitectureRules.restControllersOnlyInControllerPackage(OWN_ROOT)

    @ArchTest
    val servicesOnlyInServicePackage: ArchRule =
        ArchitectureRules.servicesOnlyInServicePackage(OWN_ROOT)

    @ArchTest
    val repositoriesOnlyInRepositoryPackage: ArchRule =
        ArchitectureRules.repositoriesOnlyInRepositoryPackage(OWN_ROOT).allowEmptyShould(true)

    @ArchTest
    val noTransactionalOnControllerClasses: ArchRule =
        ArchitectureRules.noTransactionalOnControllerClasses(OWN_ROOT)

    @ArchTest
    val noTransactionalOnControllerMethods: ArchRule =
        ArchitectureRules.noTransactionalOnControllerMethods(OWN_ROOT).allowEmptyShould(true)

    @ArchTest
    val controllerPathsAreVersioned: ArchRule =
        ArchitectureRules.controllerPathsAreVersioned(OWN_ROOT)

    @ArchTest
    val noFloatingPointInEntities: ArchRule =
        ArchitectureRules.noFloatingPointInEntities(OWN_ROOT).allowEmptyShould(true)

    @ArchTest
    val noLocalDateTimeInEntities: ArchRule =
        ArchitectureRules.noLocalDateTimeInEntities(OWN_ROOT).allowEmptyShould(true)

    @ArchTest
    val enumFieldsOnEntitiesAreStringTyped: ArchRule =
        ArchitectureRules.enumFieldsOnEntitiesAreStringTyped(OWN_ROOT).allowEmptyShould(true)

    @ArchTest
    val noSystemOut: ArchRule = ArchitectureRules.noSystemOut(OWN_ROOT)

    @ArchTest
    val noSpringBeansOnEntitiesOrDtos: ArchRule =
        ArchitectureRules.noSpringBeansOnEntitiesOrDtos(OWN_ROOT).allowEmptyShould(true)
}
