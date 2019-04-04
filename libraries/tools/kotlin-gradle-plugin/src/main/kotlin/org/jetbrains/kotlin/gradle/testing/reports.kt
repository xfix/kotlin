/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing

import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestTaskReports
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.testing.base.plugins.TestingBasePlugin
import org.jetbrains.kotlin.gradle.plugin.TaskHolder
import org.jetbrains.kotlin.gradle.tasks.KotlinTestTask
import org.jetbrains.kotlin.gradle.tasks.locateOrRegisterTask
import java.io.File

@Suppress("UnstableApiUsage")
internal val Project.testResultsDir: File
    get() = project.buildDir.resolve(TestingBasePlugin.TEST_RESULTS_DIR_NAME)

internal val Project.reportsDir: File
    get() = project.extensions.getByType(ReportingExtension::class.java).baseDir

@Suppress("UnstableApiUsage")
internal val Project.testReportsDir: File
    get() = reportsDir.resolve(TestingBasePlugin.TESTS_DIR_NAME)

internal fun KotlinTestTask.configureConventions() {
    reports.configureConventions(project, name)
    conventionMapping.map("binResultsDir") { project.testResultsDir.resolve("$name/binary") }
}

internal fun TestTaskReports.configureConventions(project: Project, name: String) {
    val htmlReport = DslObject(html)
    val xmlReport = DslObject(junitXml)

    xmlReport.conventionMapping.map("destination") { project.testResultsDir.resolve(name) }
    htmlReport.conventionMapping.map("destination") { project.testReportsDir.resolve(name) }
}

internal val Project.allTestsTask: TaskHolder<AggregateTestReport>
    get() = locateOrRegisterTask("allTests") { aggregate ->
        tasks.maybeCreate(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(aggregate)

        aggregate.reports.configureConventions(project, "all")

        aggregate.onlyIf {
            aggregate.testTasks.size > 1
        }
    }

@Suppress("UnstableApiUsage")
internal fun registerTestTask(task: AbstractTestTask) {
    val project = task.project
    project.tasks.maybeCreate(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(task)

    val allTests = project.allTestsTask.doGetTask()
    allTests.dependsOn(task)
    allTests.registerTestTask(task)

    project.gradle.taskGraph.whenReady {
        if (it.hasTask(allTests)) {
            // when [allTestsTask] task enabled
            // let all tests be executed even on failed tests
            // let all failed test be reported by [allTestsTask]:
            // - disable all reporting in test tasks
            // - enable [checkFailedTests] on [allTestsTask]

            task.ignoreFailures = true
            task.reports.html.isEnabled = false
            task.reports.junitXml.isEnabled = false

            allTests.checkFailedTests = true
            allTests.ignoreFailures = false
        }
    }
}