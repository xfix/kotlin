/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing.internal

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.tasks.testing.*
import org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdErr
import org.gradle.api.tasks.testing.TestOutputEvent.Destination.StdOut
import org.gradle.api.tasks.testing.TestResult.ResultType.*
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTestTask
import org.jetbrains.kotlin.gradle.testing.KotlinTestFailure
import java.util.*

@Suppress("unused")
class IjTestListener : TestListener, TestOutputListener {
    private val TestDescriptor.id
        get() = (this as TestDescriptorInternal).id.toString()

    private val TestDescriptor.parentId
        get() = (parent as TestDescriptorInternal?)?.id?.toString() ?: ""

    private fun ijSend(message: String) {
        println(message)
    }

    private fun shouldReport(suite: TestDescriptor) = true //suite.parent != null

    private val parents = mutableListOf("")

    override fun beforeSuite(suite: TestDescriptor) {
        if (shouldReport(suite)) {
            suite as TestDescriptorInternal
            val id = suite.displayName
            ijSend(ijSuiteStart(parents[parents.lastIndex], id, suite.displayName))
            parents.add(id)
        }
    }

    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
        if (shouldReport(suite)) {
            ijSend(
                ijLogFinish(
                    parents[parents.lastIndex - 1],
                    parents[parents.lastIndex],
                    when (result.resultType) {
                        null, SUCCESS -> "SUCCESS"
                        FAILURE -> "FAILURE"
                        SKIPPED -> "SKIPPED"
                    },
                    result.startTime,
                    result.endTime
                )
            )
            parents.removeAt(parents.lastIndex)
        }
    }

    override fun beforeTest(testDescriptor: TestDescriptor) {
        testDescriptor as TestDescriptorInternal

        ijSend(
            ijTestStart(
                parents.last(),
                testDescriptor.id.toString(),
                testDescriptor.className ?: "",
                testDescriptor.name
            )
        )
    }

    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
        val failure = result.exception as? KotlinTestFailure

        val expected = failure?.expected?.trim()
        val actual = failure?.actual
        val stackTraceString = failure?.stackTraceString
        val errorMsg = if (stackTraceString == null) failure?.message else null

        ijSend(
            ijLogFinish(
                parents.last(),
                testDescriptor.id,
                when (result.resultType) {
                    null, SUCCESS -> "SUCCESS"
                    FAILURE -> "FAILURE"
                    SKIPPED -> "SKIPPED"
                },
                result.startTime,
                result.endTime,
                failureType = if (expected?.isNotEmpty() == true || actual?.isNotEmpty() == true) "comparison" else "error",
                errorMsg = errorMsg,
                stackTrace = stackTraceString,
                expected = expected,
                actual = actual
            )
        )
    }

    override fun onOutput(testDescriptor: TestDescriptor, outputEvent: TestOutputEvent) {
        ijSend(
            ijLogOutput(
                testDescriptor.parentId,
                testDescriptor.id,
                outputEvent.message,
                when (outputEvent.destination) {
                    null, StdOut -> "StdOut"
                    StdErr -> "StdErr"
                }
            )
        )
    }

    private fun ijTestStart(parent: String, id: String, className: String, methodName: String) = buildString {
        append("<ijLog>")
        append("<event type='beforeTest'>")
        append("<test id='$id' parentId='$parent'>")
        append("<descriptor name='$methodName' className='$className' />")
        append("</test>")
        append("</event>")
        append("</ijLog>")
    }

    private fun ijSuiteStart(parent: String, id: String, name: String) = buildString {
        append("<ijLog>")
        append("<event type='beforeTest'>")
        append("<test id='$id' parentId='$parent'>")
        append("<descriptor name='$name'/>")
        append("</test>")
        append("</event>")
        append("</ijLog>")
    }

    private fun ijLogFinish(
        parent: String,
        id: String,
        resultType: String,
        startTime: Long,
        endTime: Long,
        failureType: String? = null,
        errorMsg: String? = null,
        stackTrace: String? = null,
        expected: String? = null,
        actual: String? = null
    ) = buildString {
        append("<ijLog>")
        append("<event type='afterTest'>")
        append("<test id='$id' parentId='$parent'>")
        append("<result resultType='$resultType' startTime='$startTime' endTime='$endTime'>")
        if (failureType != null) append("<failureType>$failureType</failureType>")
        if (expected != null) append("<expected>${expected.asCData()}</expected>")
        if (actual != null) append("<actual>${actual.asCData()}</actual>")
        if (errorMsg != null) append("<errorMsg>${errorMsg.asCData()}</errorMsg>")
        if (stackTrace != null) append("<stackTrace>${stackTrace.asCData()}</stackTrace>")
        append("</result>")
        append("</test>")
        append("</event>")
        append("</ijLog>")
    }

    private fun ijLogOutput(parent: String, id: String, info: String, dest: String) = buildString {
        append("<ijLog>")
        append("<event type='onOutput'>")
        append("<test id='$id' parentId='$parent'>")
        append("<event destination='$dest'>")
        append(info.asCData())
        append("</event>")
        append("</test>")
        append("</event>")
        append("</ijLog>")
    }

    private fun ByteArray.base64() = String(Base64.getEncoder().encode(this))

    private fun String.asCData() = "<![CDATA[${toByteArray().base64()}]]>"

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun attachTo(task: AbstractTestTask) {
            if (System.getProperty("idea.active") != null) {
                if (task !is KotlinJvmTestTask) {
                    task.extensions.extraProperties.set("idea.internal.test", true)
                    val listener = IjTestListener()
                    task.addTestListener(listener)
                    task.addTestOutputListener(listener)
                    task.testLogging.showStandardStreams = false
                }
            }
        }
    }
}