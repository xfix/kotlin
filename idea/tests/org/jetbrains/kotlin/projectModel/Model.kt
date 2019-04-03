/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.projectModel

import org.jetbrains.kotlin.resolve.TargetPlatform
import org.jetbrains.kotlin.utils.Printer
import java.io.File

abstract class ProjectStructureResolveModel<M : ResolveModule>(val modules: List<M>)

abstract class ProjectStructureResolveModelBuilder<M : ResolveModule, P : ProjectStructureResolveModel<M>> {
    val modules: MutableList<ResolveModuleBuilder<M>> = mutableListOf()

    abstract fun build(): P
}

abstract class ResolveModule(
    val name: String,
    val root: File,
    val platform: TargetPlatform,
    val dependencies: List<ResolveModule>
) {
    final override fun toString(): String {
        return buildString { renderDescription(Printer(this)) }
    }

    open fun renderDescription(printer: Printer) {
        printer.println("Module $name")
        printer.pushIndent()
        printer.println("platform=$platform")
        printer.println("root=${root.absolutePath}")
        printer.println("dependencies=${dependencies.joinToString { it.name }}")
    }
}

abstract class ResolveModuleBuilder<M : ResolveModule> {
    private var state: State = State.NOT_BUILT
    private var cachedResult: M? = null

    var name: String? = null
    var root: File? = null
    var platform: TargetPlatform? = null
    val dependencies: MutableList<ResolveModuleBuilder<M>> = mutableListOf()

    open fun build(): M {
        if (state == State.BUILT) return cachedResult!!

        require(state == State.NOT_BUILT) { "Re-building module $this with name $name (root at $root)" }
        state = State.BUILDING

        val builtDependencies = dependencies.map { it.build() }
        cachedResult = doBuild(builtDependencies)
        state = State.BUILT

        return cachedResult!!
    }

    /** Override it to built more specific type */
    abstract fun doBuild(builtDependencies: List<ResolveModule>): M

    enum class State {
        NOT_BUILT,
        BUILDING,
        BUILT
    }
}