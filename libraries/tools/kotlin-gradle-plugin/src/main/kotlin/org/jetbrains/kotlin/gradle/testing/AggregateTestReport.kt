/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing

import org.gradle.api.GradleException
import org.gradle.api.internal.tasks.testing.junit.result.AggregateTestResultsProvider
import org.gradle.api.internal.tasks.testing.junit.result.BinaryResultBackedTestResultsProvider
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.*
import org.gradle.internal.concurrent.CompositeStoppable.stoppable
import org.gradle.internal.logging.ConsoleRenderer
import java.util.*

open class AggregateTestReport : TestReport() {
    @Internal
    val testTasks = mutableListOf<AbstractTestTask>()

    @Input
    var ignoreFailures: Boolean = false

    private var hasFailedTests = false

    val testCountLogger: TestListener = object : TestListener {
        override fun beforeTest(testDescriptor: TestDescriptor) {
        }

        override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        }

        override fun beforeSuite(suite: TestDescriptor) {
        }

        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
            if (result.failedTestCount > 0) {
                hasFailedTests = true
            }
        }
    }

    internal var printStats: Boolean = false

    fun registerTestTask(task: AbstractTestTask) {
        testTasks.add(task)
        task.addTestListener(testCountLogger)
        reportOn(task)
    }

    @TaskAction
    fun checkTestResults() {
//        val junitXml = reports.getJunitXml()
//        if (junitXml.isEnabled()) {
//            val outputAssociation = if (junitXml.isOutputPerTestCase())
//                TestOutputAssociation.WITH_TESTCASE
//            else
//                TestOutputAssociation.WITH_SUITE
//            val binary2JUnitXmlReportGenerator = Binary2JUnitXmlReportGenerator(
//                junitXml.getDestination(), testResultsProvider, outputAssociation,
//                buildOperationExecutor, getInetAddressFactory().getHostname()
//            )
//            binary2JUnitXmlReportGenerator.generate()
//        }

        if (printStats && hasFailedTests) {
            val reportUrl = ConsoleRenderer().asClickableFileUrl(destinationDir.resolve("index.html"))
            val message = "There were failing tests. See the report at: $reportUrl"

            if (ignoreFailures) {
                logger.warn(message)
            } else {
                throw GradleException(message)
            }
        }
    }

    private fun createAggregateProvider(): TestResultsProvider {
        val resultsProviders = LinkedList<TestResultsProvider>()
        try {
            val resultDirs = testResultDirs
            return if (resultDirs.files.size == 1) BinaryResultBackedTestResultsProvider(resultDirs.singleFile)
            else AggregateTestResultsProvider(resultDirs.mapTo(resultsProviders) { BinaryResultBackedTestResultsProvider(it) })
        } catch (e: RuntimeException) {
            stoppable(resultsProviders).stop()
            throw e
        }
    }
}