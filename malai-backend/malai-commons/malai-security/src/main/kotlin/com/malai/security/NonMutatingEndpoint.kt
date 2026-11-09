package com.malai.security

/**
 * Marks a controller method whose HTTP verb is POST/PUT/PATCH/DELETE but which does not
 * change persistent state — e.g. Confirmation of Payee lookup. Such methods are exempt
 * from `ArchitectureRules.mutatingControllersReachAuditWriter`.
 *
 * Use sparingly. The annotation is reviewable: a grep surfaces every endpoint that opted
 * out of the audit-reach invariant. Reviewers should re-check on every change.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NonMutatingEndpoint
