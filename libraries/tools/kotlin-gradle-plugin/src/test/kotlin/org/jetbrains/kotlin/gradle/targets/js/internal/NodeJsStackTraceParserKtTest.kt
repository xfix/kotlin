/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.internal

import org.junit.Test
import kotlin.test.assertEquals

class NodeJsStackTraceParserKtTest {
    @Test
    fun parseNodeJsStackTrace() {
        val parsed = parseNodeJsStackTrace(
            """
AssertionError: Expected <12>, actual <42>.
    at AssertionError_init_0 (/my/project/build/tmp/expandedArchives/kotlin-stdlib-js-1.3-SNAPSHOT.jar_730a1b227513cf16a9b639e009a985fc/kotlin/exceptions.kt:102:37)
    at DefaultJsAsserter.failWithMessage_0 (/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/src/main/kotlin/kotlin/test/DefaultJsAsserter.kt:80:19)
    at DefaultJsAsserter.assertTrue_o10pc4${'$'} (/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/src/main/kotlin/kotlin/test/DefaultJsAsserter.kt:60:13)
    at DefaultJsAsserter.Asserter.assertEquals_lzc6tz${'$'} (/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/Assertions.kt:189:9)
    at DefaultJsAsserter.assertEquals_lzc6tz${'$'} (/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/src/main/kotlin/kotlin/test/DefaultJsAsserter.kt:27:15)
    at assertEquals (/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/Assertions.kt:50:14)
    at SampleTests.testMe (/my/project/src/commonTest/kotlin/sample/SampleTests.kt:9:9)
    at /my/project/build/js_test_node_modules/mpplib2_test.js:77:36
    at Object.fn [as test] (/my/project/build/tmp/expandedArchives/src/KotlinTestRunner.ts:12:25)
    at Object.test (/my/project/build/tmp/expandedArchives/src/KotlinTestTeamCityReporter.ts:80:28)                
            """.trimIndent()
        ).toString()

        assertEquals(
            """
NodeJsStackTrace(
message="AssertionError: Expected <12>, actual <42>.",
stacktrace=[
NodeJsStackTraceElement(className=AssertionError, methodName=init, fileName=/my/project/build/tmp/expandedArchives/kotlin-stdlib-js-1.3-SNAPSHOT.jar_730a1b227513cf16a9b639e009a985fc/kotlin/exceptions.kt, lineNumber=102, colNumber=37)
NodeJsStackTraceElement(className=DefaultJsAsserter, methodName=failWithMessage, fileName=/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/src/main/kotlin/kotlin/test/DefaultJsAsserter.kt, lineNumber=80, colNumber=19)
NodeJsStackTraceElement(className=DefaultJsAsserter, methodName=assertTrue, fileName=/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/src/main/kotlin/kotlin/test/DefaultJsAsserter.kt, lineNumber=60, colNumber=13)
NodeJsStackTraceElement(className=DefaultJsAsserter.Asserter, methodName=assertEquals, fileName=/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/Assertions.kt, lineNumber=189, colNumber=9)
NodeJsStackTraceElement(className=DefaultJsAsserter, methodName=assertEquals, fileName=/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/src/main/kotlin/kotlin/test/DefaultJsAsserter.kt, lineNumber=27, colNumber=15)
NodeJsStackTraceElement(className=null, methodName=assertEquals, fileName=/my/project/build/tmp/expandedArchives/kotlin-test-js-1.3-SNAPSHOT.jar_d60f1e6d0dd94843a03bf98a569bbb73/Assertions.kt, lineNumber=50, colNumber=14)
NodeJsStackTraceElement(className=SampleTests, methodName=testMe, fileName=/my/project/src/commonTest/kotlin/sample/SampleTests.kt, lineNumber=9, colNumber=9)
NodeJsStackTraceElement(className=null, methodName=null, fileName= /my/project/build/js_test_node_modules/mpplib2_test.js, lineNumber=77, colNumber=36)
NodeJsStackTraceElement(className=Object, methodName=test, fileName=/my/project/build/tmp/expandedArchives/src/KotlinTestRunner.ts, lineNumber=12, colNumber=25)
NodeJsStackTraceElement(className=Object, methodName=test, fileName=/my/project/build/tmp/expandedArchives/src/KotlinTestTeamCityReporter.ts, lineNumber=80, colNumber=28)
])
            """.trim(), parsed
        )
    }
}