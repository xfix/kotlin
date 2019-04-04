/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.*
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.base.plugins.TestingBasePlugin
import org.jetbrains.kotlin.gradle.plugin.TaskHolder
import org.jetbrains.kotlin.gradle.tasks.KotlinTestTask
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import org.jetbrains.kotlin.gradle.utils.injected
import java.io.File
import javax.inject.Inject

@Suppress("UnstableApiUsage")
internal val Project.testResultsDir: File
    get() = project.buildDir.resolve(TestingBasePlugin.TEST_RESULTS_DIR_NAME)

internal val Project.reportsDir: File
    get() = project.extensions.getByType(ReportingExtension::class.java).baseDir

@Suppress("UnstableApiUsage")
internal val Project.testReportsDir: File
    get() = reportsDir.resolve(TestingBasePlugin.TESTS_DIR_NAME)

internal fun KotlinTestTask.configureConventions() {
    val htmlReport = DslObject(reports.html)
    val xmlReport = DslObject(reports.junitXml)

    xmlReport.conventionMapping.map("destination") { project.testResultsDir.resolve(name) }
    htmlReport.conventionMapping.map("destination") { project.testReportsDir.resolve(name) }
    conventionMapping.map("binResultsDir") { project.testResultsDir.resolve("$name/binary") }
}

internal val Project.aggregateTestReportTask: TaskHolder<AggregateTestReport>
    get() = locateOrRegisterTask("aggregateTestReport") {
        tasks.maybeCreate(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(it)

        it.destinationDir = testReportsDir.resolve("all")
    }

@Suppress("UnstableApiUsage")
internal fun registerTestTask(task: AbstractTestTask) {
    val project = task.project
    project.tasks.maybeCreate(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)

    val aggregateTestReportTask = project.aggregateTestReportTask.doGetTask()
    aggregateTestReportTask.dependsOn(task)
    aggregateTestReportTask.registerTestTask(task)

    project.gradle.taskGraph.whenReady {
        if (it.hasTask(aggregateTestReportTask)) {
            task.ignoreFailures = true
            task.reports.html.isEnabled = false
            task.reports.junitXml.isEnabled = false

            aggregateTestReportTask.printStats = true
            aggregateTestReportTask.ignoreFailures = false
        }
    }
}