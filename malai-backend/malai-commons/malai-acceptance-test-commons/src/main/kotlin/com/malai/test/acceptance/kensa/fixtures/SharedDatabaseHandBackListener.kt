package com.malai.test.acceptance.kensa.fixtures

import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan

/**
 * Fires [SharedDatabaseHandBack] when a test JVM's plan finishes.
 *
 * Registered through `META-INF/services` rather than a hook in each module's test base, for the
 * reason issue 02 chose an `EnvironmentPostProcessor` over a line in each `application-test.properties`:
 * a per-module list drifts, and a new acceptance-test module cannot forget this one. Every module
 * that truncates already has this one on its test classpath.
 */
class SharedDatabaseHandBackListener : TestExecutionListener {

    override fun testPlanExecutionFinished(testPlan: TestPlan) {
        SharedDatabaseHandBack.handBack()
    }
}
