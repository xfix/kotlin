/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

internal class SourceSetVisibilityProvider(
    private val project: Project
) {
    fun getVisibleSourceSetsExcludingDependsOn(
        visibleFrom: KotlinSourceSet,
        mppDependency: ResolvedDependency,
        dependencyProjectMetadata: KotlinProjectStructureMetadata
    ): Set<String> {
        val visibleByThisSourceSet = getVisibleSourceSets(visibleFrom, mppDependency, dependencyProjectMetadata)
        val visibleByParents = visibleFrom.dependsOn.map { getVisibleSourceSets(it, mppDependency, dependencyProjectMetadata) }

        return visibleByThisSourceSet
            .filterTo(mutableSetOf()) { item -> visibleByParents.none { visibleThroughDependsOn -> item in visibleThroughDependsOn } }
    }

    @Suppress("UnstableApiUsage")
    fun getVisibleSourceSets(
        visibleFrom: KotlinSourceSet,
        mppDependency: ResolvedDependency,
        dependencyProjectMetadata: KotlinProjectStructureMetadata
    ): Set<String> {
        val compilations = CompilationSourceSetUtil.compilationsBySourceSets(project).getValue(visibleFrom)

        val visiblePlatformVariantNames = compilations
            .filter { it.target.platformType != KotlinPlatformType.common }
            .mapNotNullTo(mutableSetOf()) {
                project.configurations.getByName(it.compileDependencyConfigurationName)
                    // Resolve the configuration but don't trigger artifacts download, only download component metainformation:
                    .incoming.resolutionResult.allComponents
                    .find { it.moduleVersion?.group == mppDependency.moduleGroup && it.moduleVersion?.name == mppDependency.moduleName }
                    ?.variant?.displayName
            }

        return dependencyProjectMetadata.sourceSetNamesByVariantName
            .filterKeys { it in visiblePlatformVariantNames }
            .values.let { if (it.isEmpty()) emptySet() else it.reduce { acc, item -> acc intersect item } }
    }
}