/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.gradle.execution

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.execution.debugger.CidrDebugProcess

class IdeaGradleKonanLauncher(
    private val myEnvironment: ExecutionEnvironment,
    private val myConfiguration: GradleKonanAppRunConfiguration
) : GradleKonanLauncher() {
    override fun createDebugProcess(p0: CommandLineState, p1: XDebugSession): CidrDebugProcess {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createProcess(p0: CommandLineState): ProcessHandler {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getProject(): Project {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}