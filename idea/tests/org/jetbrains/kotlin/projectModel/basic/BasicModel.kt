/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.projectModel.basic

import org.jetbrains.kotlin.projectModel.ProjectStructureResolveModel
import org.jetbrains.kotlin.projectModel.ProjectStructureResolveModelBuilder
import org.jetbrains.kotlin.projectModel.ResolveModule
import org.jetbrains.kotlin.projectModel.ResolveModuleBuilder
import org.jetbrains.kotlin.resolve.TargetPlatform
import java.io.File

open class BasicResolveModule(
    name: String,
    root: File,
    platform: TargetPlatform,
    dependencies: List<ResolveModule>
) : ResolveModule(name, root, platform, dependencies)

open class BasicResolveModuleBuilder : ResolveModuleBuilder<BasicResolveModule>() {
    override fun doBuild(builtDependencies: List<ResolveModule>): BasicResolveModule {
        return BasicResolveModule(name!!, root!!, platform!!, builtDependencies)
    }
}

open class BasicProjectStructureResolveModel(modules: List<BasicResolveModule>) :
    ProjectStructureResolveModel<BasicResolveModule>(modules)

open class BasicProjectStructureResolveModelBulder :
    ProjectStructureResolveModelBuilder<BasicResolveModule, BasicProjectStructureResolveModel>() {
    override fun build(): BasicProjectStructureResolveModel {
        val builtModules = modules.map { it.build() }
        return BasicProjectStructureResolveModel(builtModules)
    }
}