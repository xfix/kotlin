/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing.internal

import org.gradle.api.tasks.testing.AbstractTestTask
import java.net.URLClassLoader

/**
 * Experimental test reporting for Intellij Ultimate only
 */
internal fun ijListenTestTask(task: AbstractTestTask) {
    Class.forName("org.jetbrains.kotlin.gradle.testing.internal.IjTestListener")
        ?.getMethod("attachTo")
        ?.invoke(null, task)
}