package com.malai.copilot.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

class NoCopilotClientPairTest {

    @Test
    fun thereIsDeliberatelyNoCopilotClientModule() {
        val clientModules = File(REPO_ROOT, "malai-clients")
        clientModules.isDirectory shouldBe true

        clientModules.list().orEmpty()
            .filter { it.contains("copilot") }
            .toList()
            .shouldBeEmpty()
    }

    @Test
    fun noCopilotClientTypeIsReachableFromAnyService() {
        ClassFileImporter()
            .importPackages("com.malai.client")
            .filter { it.packageName.contains(".copilot") }
            .map { it.name }
            .shouldBeEmpty()
    }

    private companion object {
        val REPO_ROOT: File = File(System.getProperty("user.dir")).resolve("../..").normalize()
    }
}
