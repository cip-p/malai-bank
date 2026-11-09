package com.malai.security

/**
 * Marker for controller methods that are NOT user-facing — service-to-service
 * onboarding paths, admin-only operational endpoints, debug surfaces.
 *
 * The annotation is a marker only. It does NOT exempt a method from `@PreAuthorize`:
 * internal endpoints must still declare an explicit scope check (typically
 * `@PreAuthorize("hasAuthority('SCOPE_<scope>')")`). The combination — annotation
 * present plus matching `/api/v<n>/internal/` path — is enforced by
 * `ArchitectureRules.internalEndpointAnnotationOnInternalPathsOnly`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class InternalEndpoint
